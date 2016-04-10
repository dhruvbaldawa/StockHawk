package com.sam_chordas.android.stockhawk.widget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;

/**
 * Created by dhruv on 4/6/16.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class QuoteWidgetRemoteViewsService extends RemoteViewsService{
    private static final String LOG_TAG = QuoteWidgetRemoteViewsService.class.getSimpleName();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new StockRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    class StockRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory  {
        private Context mContext;
        private Cursor mCursor = null;
        private int mAppWidgetId;

        public StockRemoteViewsFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {

        }

        @Override
        public void onDataSetChanged() {
            if (mCursor != null) {
                mCursor.close();
            }

            final long identityToken = Binder.clearCallingIdentity();
            mCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[]{QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.NAME, QuoteColumns.BIDPRICE,
                            QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
                    QuoteColumns.ISCURRENT + " = ?",
                    new String[]{"1"},
                    null);
            Binder.restoreCallingIdentity(identityToken);
        }

        @Override
        public void onDestroy() {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
        }

        @Override
        public int getCount() {
            return (mCursor == null) ? 0 : mCursor.getCount();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position == AdapterView.INVALID_POSITION || mCursor == null ||
                    !mCursor.moveToPosition(position)) {
                return null;
            }

            String symbol = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.SYMBOL));
            String name = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.NAME));
            RemoteViews views = new RemoteViews(mContext.getPackageName(), R.layout.widget_collection_item);
            views.setTextViewText(R.id.stock_symbol, symbol);
            views.setContentDescription(R.id.stock_symbol, mContext.getString(R.string.a11y_symbol, name, symbol));
            views.setTextViewText(R.id.bid_price, mCursor.getString(mCursor.getColumnIndex(QuoteColumns.BIDPRICE)));
            String strChange;
            if (Utils.showPercent) {
                strChange = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.PERCENT_CHANGE));
            } else {
                strChange = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.CHANGE));
            }

            if (mCursor.getInt(mCursor.getColumnIndex(QuoteColumns.ISUP)) == 1) {
                views.setTextColor(R.id.change, Color.GREEN);
            } else {
                views.setTextColor(R.id.change, Color.RED);
            }
            views.setTextViewText(R.id.change, strChange);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
