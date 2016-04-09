package com.sam_chordas.android.stockhawk.ui;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class LineGraphActivity extends AppCompatActivity {
    private static final String LOG_TAG = LineGraphActivity.class.getSimpleName();
    private static final int HTTP_REQUEST_TAG = 0;

    private LineChart mLineChart;
    private OkHttpClient mHttpClient;
    private String mSymbol;

    private static final int TIME_7_DAYS = 1;
    private static final int TIME_30_DAYS = 2;
    private static final int TIME_6_MONTHS = 3;
    private static final int TIME_YEAR = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            mSymbol = getIntent().getStringExtra("symbol");
        } else {
            mSymbol = savedInstanceState.getString("symbol");
        }

        setContentView(R.layout.activity_line_graph);
        setTitle(mSymbol);
        mLineChart = (LineChart) findViewById(R.id.linechart);
        mLineChart.setNoDataText(getString(R.string.line_graph_chart_no_data));
        mLineChart.setBorderColor(Color.WHITE);
        mLineChart.setDescription("");
        mLineChart.setDescriptionColor(Color.WHITE);

        mLineChart.getXAxis().setTextColor(Color.WHITE);
        mLineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        mLineChart.getAxisLeft().setTextColor(Color.YELLOW);
        mLineChart.getAxisRight().setTextColor(Color.YELLOW);
        mLineChart.getAxisRight().setDrawTopYLabelEntry(false);

        Legend legend = mLineChart.getLegend();
        legend.setTextColor(Color.WHITE);

        mHttpClient = new OkHttpClient();

        RadioButton defaultRadio = (RadioButton)findViewById(R.id.radio_button_30day);
        defaultRadio.toggle();
        onRadioButtonClicked(defaultRadio);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("symbol", mSymbol);
        super.onSaveInstanceState(outState);
    }

    private void refreshHistoricalData(int timeFrame) {
        mLineChart.clear();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        Date today = calendar.getTime();
        switch (timeFrame) {
            case TIME_7_DAYS:
                calendar.add(Calendar.DAY_OF_MONTH, -7);
                break;
            case TIME_30_DAYS:
                calendar.add(Calendar.DAY_OF_MONTH, -30);
                break;
            case TIME_6_MONTHS:
                calendar.add(Calendar.MONTH, -6);
                break;
            case TIME_YEAR:
                calendar.add(Calendar.YEAR, -1);
        }
        Date fromDate = calendar.getTime();
        fetchHistoricalData(mSymbol, dateFormat.format(fromDate), dateFormat.format(today));
    }

    private void fetchHistoricalData(String symbol, String startDate, String endDate) {
        final String YAHOO_API_BASE_URL = "https://query.yahooapis.com/v1/public/yql";
        final String query = "SELECT Date, Close FROM yahoo.finance.historicaldata where symbol = \"" +
                symbol + "\" and startDate = \"" + startDate + "\" and endDate = \"" + endDate + "\"";

        Uri builtUri = Uri.parse(YAHOO_API_BASE_URL).buildUpon()
                .appendQueryParameter("env", "store://datatables.org/alltableswithkeys")
                .appendQueryParameter("format", "json")
                .appendQueryParameter("q", query)
                .build();

        Request request = new Request.Builder()
                .url(builtUri.toString())
                .tag(HTTP_REQUEST_TAG)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Utils.showToastAsync(LineGraphActivity.this,
                        R.string.toast_network_failure, Toast.LENGTH_LONG);
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

                    if (ViewCompat.getLayoutDirection(mLineChart) == ViewCompat.LAYOUT_DIRECTION_LTR) {
                        // adding the data in reverse
                        for (int i = count - 1; i >= 0; i--) {
                            xValues.add(quotesJSON.getJSONObject(i).getString(API_DATE_KEY));
                            yValues.add(new Entry(Float.parseFloat(
                                    quotesJSON.getJSONObject(i).getString(API_CLOSE_KEY)), count - i + 1));
                        }
                    } else {
                        for (int i = 0; i < count; i++) {
                            xValues.add(quotesJSON.getJSONObject(i).getString(API_DATE_KEY));
                            yValues.add(new Entry(Float.parseFloat(
                                    quotesJSON.getJSONObject(i).getString(API_CLOSE_KEY)), i));

                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                final LineDataSet dataSet = new LineDataSet(yValues, mSymbol);
                dataSet.setValueTextColor(Color.LTGRAY);
                dataSet.setLineWidth(2.0f);
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

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        switch (view.getId()) {
            case R.id.radio_button_7day:
                if (checked) {
                    refreshHistoricalData(TIME_7_DAYS);
                }
                break;
            case R.id.radio_button_30day:
                if (checked) {
                    refreshHistoricalData(TIME_30_DAYS);
                }
                break;
            case R.id.radio_button_6month:
                if (checked) {
                    refreshHistoricalData(TIME_6_MONTHS);
                }
                break;
            case R.id.radio_button_1year:
                if (checked) {
                    refreshHistoricalData(TIME_YEAR);
                }
                break;
        }
    }
}
