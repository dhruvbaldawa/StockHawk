package com.sam_chordas.android.stockhawk.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService {
    private String LOG_TAG = StockTaskService.class.getSimpleName();
    public static final String STOCK_DATA_UPDATED = "com.sam_chordas.android.stockhawk.STOCK_DATA_UPDATED";

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private boolean isUpdate;

    public StockTaskService() {
    }

    public StockTaskService(Context context) {
        mContext = context;
    }

    String fetchData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("error sending request: " + response);
        return response.body().string();
    }

    @Override
    public int onRunTask(TaskParams params) {
        Cursor initQueryCursor;
        if (mContext == null) {
            mContext = this;
        }
        final String YAHOO_API_BASE_URL = "https://query.yahooapis.com/v1/public/yql";
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM yahoo.finance.quotes where symbol in (");

        Uri.Builder uriBuilder = Uri.parse(YAHOO_API_BASE_URL).buildUpon()
                .appendQueryParameter("env", "store://datatables.org/alltableswithkeys")
                .appendQueryParameter("format", "json");

        if (params.getTag().equals("init") || params.getTag().equals("periodic")) {
            isUpdate = true;
            initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[]{"Distinct " + QuoteColumns.SYMBOL}, null,
                    null, null);
            if (initQueryCursor.getCount() == 0 || initQueryCursor == null) {
                // Init task. Populates DB with quotes for the symbols seen below
                queryBuilder.append("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")");
            } else if (initQueryCursor != null) {
                DatabaseUtils.dumpCursor(initQueryCursor);
                initQueryCursor.moveToFirst();
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    queryBuilder.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                queryBuilder.replace(queryBuilder.length() - 1, queryBuilder.length(), ")");
            }
            initQueryCursor.close();
        } else if (params.getTag().equals("add")) {
            isUpdate = false;
            // get symbol from params.getExtra and build query
            String stockInput = params.getExtras().getString("symbol").toUpperCase();
            queryBuilder.append("\"" + stockInput + "\")");
        }
        // finalize the URL for the API query.
        uriBuilder.appendQueryParameter("q", queryBuilder.toString());

        String urlString = uriBuilder.build().toString();
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        try {
            getResponse = fetchData(urlString);
        } catch (IOException e) {
            e.printStackTrace();
            Utils.showToastAsync(mContext, R.string.toast_network_failure, Toast.LENGTH_LONG);
            return result;
        }

        result = GcmNetworkManager.RESULT_SUCCESS;
        try {
            ContentValues contentValues = new ContentValues();
            // update ISCURRENT to 0 (false) so new data is current
            if (isUpdate) {
                contentValues.put(QuoteColumns.ISCURRENT, 0);
                mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                        null, null);
            }
            ArrayList contentVals = Utils.quoteJsonToContentVals(getResponse);
            if (contentVals == null) {
                Utils.showToastAsync(mContext, R.string.toast_symbol_not_found, Toast.LENGTH_LONG);
                return GcmNetworkManager.RESULT_FAILURE;
            }
            mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY, contentVals);
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(LOG_TAG, "Error applying batch insert", e);
        }

        Intent dataUpdatedIntent = new Intent(STOCK_DATA_UPDATED).setPackage(mContext.getPackageName());
        mContext.sendBroadcast(dataUpdatedIntent);
        return result;
    }
}
