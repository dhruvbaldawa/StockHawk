package com.sam_chordas.android.stockhawk.ui;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.sam_chordas.android.stockhawk.R;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

public class LineGraphActivity extends Activity{
    private static final String LOG_TAG = LineGraphActivity.class.getSimpleName();
    private static final int HTTP_REQUEST_TAG = 0;

    private Context mContext;
    private LineChart mLineChart;
    private OkHttpClient mHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        setContentView(R.layout.activity_line_graph);
        mLineChart = (LineChart) findViewById(R.id.linechart);

        mHttpClient = new OkHttpClient();
        fetchHistoricalData("YHOO", "2015-02-09", "2015-03-09");
    }

    private void fetchHistoricalData(String symbol, String startDate, String endDate) {
        final String YAHOO_API_BASE_URL = "https://query.yahooapis.com/v1/public/yql";
        final String query = "SELECT Date, Close FROM yahoo.finance.historicaldata where symbol = \"" +
                symbol + "\" and startDate = \"" + startDate + "\" and endDate = \"" + endDate + "\"";

        Uri builtUri = Uri.parse(YAHOO_API_BASE_URL).buildUpon()
                .appendQueryParameter("env", "store://datatables.org/alltableswithkeys")
                .appendQueryParameter("q", query)
                .appendQueryParameter("format", "json")
                .build();

        Request request = new Request.Builder()
                .url(builtUri.toString())
                .tag(HTTP_REQUEST_TAG)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Error fetching historical data: " + response);
                }

                final String API_QUERY_KEY = "query";
                final String API_RESULTS_KEY = "results";
                final String API_QUOTES_KEY = "quote";
                final String API_COUNT_KEY = "count";
                final String API_CLOSE_KEY = "Close";
                final String API_DATE_KEY = "Date";

                ArrayList<String> xValues = new ArrayList<String>();
                ArrayList<Entry> yValues = new ArrayList<Entry>();

                try {
                    JSONObject responseJSON = new JSONObject(response.body().string());
                    int count = responseJSON.getJSONObject(API_QUERY_KEY).getInt(API_COUNT_KEY);
                    JSONArray quotesJSON = responseJSON.getJSONObject(API_QUERY_KEY)
                            .getJSONObject(API_RESULTS_KEY)
                            .getJSONArray(API_QUOTES_KEY);

                    // adding the data in reverse
                    for (int i = count - 1; i >= 0; i--) {
                        xValues.add(quotesJSON.getJSONObject(i).getString(API_DATE_KEY));
                        yValues.add(new Entry(Float.parseFloat(
                                quotesJSON.getJSONObject(i).getString(API_CLOSE_KEY)), i));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                final LineDataSet dataSet = new LineDataSet(yValues, "Dataset");
                ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
                dataSets.add(dataSet);

                final LineData data = new LineData(xValues, dataSets);
                LineGraphActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLineChart.setData(data);
                        mLineChart.invalidate();
                        Log.d(LOG_TAG, "run: done with fetching all the data");
                    }
                });
            }
        });
    }
}
