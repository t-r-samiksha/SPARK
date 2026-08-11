package com.spark.wallet.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.spark.wallet.data.entity.PendingRelay;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PendingRelayDao_Impl implements PendingRelayDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PendingRelay> __insertionAdapterOfPendingRelay;

  private final SharedSQLiteStatement __preparedStmtOfDeletePendingRelay;

  private final SharedSQLiteStatement __preparedStmtOfDeleteExpiredRelays;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public PendingRelayDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPendingRelay = new EntityInsertionAdapter<PendingRelay>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `pending_relay` (`tx_id`,`blob`,`destination_hint`,`ttl`,`received_at`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PendingRelay entity) {
        statement.bindString(1, entity.getTxId());
        statement.bindString(2, entity.getBlob());
        if (entity.getDestinationHint() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getDestinationHint());
        }
        statement.bindLong(4, entity.getTtl());
        statement.bindLong(5, entity.getReceivedAt());
      }
    };
    this.__preparedStmtOfDeletePendingRelay = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_relay WHERE tx_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteExpiredRelays = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_relay WHERE ttl < ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_relay";
        return _query;
      }
    };
  }

  @Override
  public Object insertPendingRelay(final PendingRelay relay,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPendingRelay.insert(relay);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<PendingRelay> relays,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPendingRelay.insert(relays);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePendingRelay(final String txId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePendingRelay.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, txId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeletePendingRelay.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteExpiredRelays(final long currentTime,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteExpiredRelays.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, currentTime);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteExpiredRelays.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getPendingRelayById(final String txId,
      final Continuation<? super PendingRelay> $completion) {
    final String _sql = "SELECT * FROM pending_relay WHERE tx_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, txId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PendingRelay>() {
      @Override
      @Nullable
      public PendingRelay call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "blob");
          final int _cursorIndexOfDestinationHint = CursorUtil.getColumnIndexOrThrow(_cursor, "destination_hint");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfReceivedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "received_at");
          final PendingRelay _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpBlob;
            _tmpBlob = _cursor.getString(_cursorIndexOfBlob);
            final String _tmpDestinationHint;
            if (_cursor.isNull(_cursorIndexOfDestinationHint)) {
              _tmpDestinationHint = null;
            } else {
              _tmpDestinationHint = _cursor.getString(_cursorIndexOfDestinationHint);
            }
            final long _tmpTtl;
            _tmpTtl = _cursor.getLong(_cursorIndexOfTtl);
            final long _tmpReceivedAt;
            _tmpReceivedAt = _cursor.getLong(_cursorIndexOfReceivedAt);
            _result = new PendingRelay(_tmpTxId,_tmpBlob,_tmpDestinationHint,_tmpTtl,_tmpReceivedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllPendingRelays(final Continuation<? super List<PendingRelay>> $completion) {
    final String _sql = "SELECT * FROM pending_relay ORDER BY received_at ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PendingRelay>>() {
      @Override
      @NonNull
      public List<PendingRelay> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "blob");
          final int _cursorIndexOfDestinationHint = CursorUtil.getColumnIndexOrThrow(_cursor, "destination_hint");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfReceivedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "received_at");
          final List<PendingRelay> _result = new ArrayList<PendingRelay>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingRelay _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpBlob;
            _tmpBlob = _cursor.getString(_cursorIndexOfBlob);
            final String _tmpDestinationHint;
            if (_cursor.isNull(_cursorIndexOfDestinationHint)) {
              _tmpDestinationHint = null;
            } else {
              _tmpDestinationHint = _cursor.getString(_cursorIndexOfDestinationHint);
            }
            final long _tmpTtl;
            _tmpTtl = _cursor.getLong(_cursorIndexOfTtl);
            final long _tmpReceivedAt;
            _tmpReceivedAt = _cursor.getLong(_cursorIndexOfReceivedAt);
            _item = new PendingRelay(_tmpTxId,_tmpBlob,_tmpDestinationHint,_tmpTtl,_tmpReceivedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PendingRelay>> getAllPendingRelaysFlow() {
    final String _sql = "SELECT * FROM pending_relay ORDER BY received_at ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"pending_relay"}, new Callable<List<PendingRelay>>() {
      @Override
      @NonNull
      public List<PendingRelay> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "blob");
          final int _cursorIndexOfDestinationHint = CursorUtil.getColumnIndexOrThrow(_cursor, "destination_hint");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfReceivedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "received_at");
          final List<PendingRelay> _result = new ArrayList<PendingRelay>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingRelay _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpBlob;
            _tmpBlob = _cursor.getString(_cursorIndexOfBlob);
            final String _tmpDestinationHint;
            if (_cursor.isNull(_cursorIndexOfDestinationHint)) {
              _tmpDestinationHint = null;
            } else {
              _tmpDestinationHint = _cursor.getString(_cursorIndexOfDestinationHint);
            }
            final long _tmpTtl;
            _tmpTtl = _cursor.getLong(_cursorIndexOfTtl);
            final long _tmpReceivedAt;
            _tmpReceivedAt = _cursor.getLong(_cursorIndexOfReceivedAt);
            _item = new PendingRelay(_tmpTxId,_tmpBlob,_tmpDestinationHint,_tmpTtl,_tmpReceivedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
