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
import com.spark.wallet.data.entity.CachedCert;
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
public final class CachedCertDao_Impl implements CachedCertDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CachedCert> __insertionAdapterOfCachedCert;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCert;

  private final SharedSQLiteStatement __preparedStmtOfDeleteExpiredCerts;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public CachedCertDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCachedCert = new EntityInsertionAdapter<CachedCert>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cached_certs` (`device_id`,`public_key`,`cert_blob`,`expires_at`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CachedCert entity) {
        statement.bindString(1, entity.getDeviceId());
        statement.bindString(2, entity.getPublicKey());
        statement.bindString(3, entity.getCertBlob());
        statement.bindLong(4, entity.getExpiresAt());
      }
    };
    this.__preparedStmtOfDeleteCert = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cached_certs WHERE device_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteExpiredCerts = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cached_certs WHERE expires_at < ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cached_certs";
        return _query;
      }
    };
  }

  @Override
  public Object insertCert(final CachedCert cert, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCachedCert.insert(cert);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<CachedCert> certs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCachedCert.insert(certs);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCert(final String deviceId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCert.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, deviceId);
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
          __preparedStmtOfDeleteCert.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteExpiredCerts(final long currentTime,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteExpiredCerts.acquire();
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
          __preparedStmtOfDeleteExpiredCerts.release(_stmt);
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
  public Object getCertByDeviceId(final String deviceId,
      final Continuation<? super CachedCert> $completion) {
    final String _sql = "SELECT * FROM cached_certs WHERE device_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, deviceId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedCert>() {
      @Override
      @Nullable
      public CachedCert call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "device_id");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "public_key");
          final int _cursorIndexOfCertBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "cert_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final CachedCert _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpCertBlob;
            _tmpCertBlob = _cursor.getString(_cursorIndexOfCertBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _result = new CachedCert(_tmpDeviceId,_tmpPublicKey,_tmpCertBlob,_tmpExpiresAt);
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
  public Object getAllCerts(final Continuation<? super List<CachedCert>> $completion) {
    final String _sql = "SELECT * FROM cached_certs ORDER BY expires_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CachedCert>>() {
      @Override
      @NonNull
      public List<CachedCert> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "device_id");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "public_key");
          final int _cursorIndexOfCertBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "cert_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final List<CachedCert> _result = new ArrayList<CachedCert>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CachedCert _item;
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpCertBlob;
            _tmpCertBlob = _cursor.getString(_cursorIndexOfCertBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _item = new CachedCert(_tmpDeviceId,_tmpPublicKey,_tmpCertBlob,_tmpExpiresAt);
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
  public Flow<List<CachedCert>> getAllCertsFlow() {
    final String _sql = "SELECT * FROM cached_certs ORDER BY expires_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cached_certs"}, new Callable<List<CachedCert>>() {
      @Override
      @NonNull
      public List<CachedCert> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "device_id");
          final int _cursorIndexOfPublicKey = CursorUtil.getColumnIndexOrThrow(_cursor, "public_key");
          final int _cursorIndexOfCertBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "cert_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final List<CachedCert> _result = new ArrayList<CachedCert>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CachedCert _item;
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpPublicKey;
            _tmpPublicKey = _cursor.getString(_cursorIndexOfPublicKey);
            final String _tmpCertBlob;
            _tmpCertBlob = _cursor.getString(_cursorIndexOfCertBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _item = new CachedCert(_tmpDeviceId,_tmpPublicKey,_tmpCertBlob,_tmpExpiresAt);
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
