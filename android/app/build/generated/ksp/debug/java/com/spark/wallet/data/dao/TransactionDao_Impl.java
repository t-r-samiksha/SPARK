package com.spark.wallet.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.spark.wallet.data.entity.TransactionRecord;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionRecord> __insertionAdapterOfTransactionRecord;

  public TransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionRecord = new EntityInsertionAdapter<TransactionRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transactions` (`tx_id`,`token_id`,`amount_paise`,`payer_device_id`,`payer_account_id`,`payee_device_id`,`payee_account_id`,`device_counter`,`prev_tx_hash`,`timestamp`,`signature`,`is_synced`,`raw_json`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionRecord entity) {
        statement.bindString(1, entity.getTxId());
        statement.bindString(2, entity.getTokenId());
        statement.bindLong(3, entity.getAmountPaise());
        statement.bindString(4, entity.getPayerDeviceId());
        statement.bindString(5, entity.getPayerAccountId());
        statement.bindString(6, entity.getPayeeDeviceId());
        statement.bindString(7, entity.getPayeeAccountId());
        statement.bindLong(8, entity.getDeviceCounter());
        if (entity.getPrevTxHash() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getPrevTxHash());
        }
        statement.bindLong(10, entity.getTimestamp());
        statement.bindString(11, entity.getSignature());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindString(13, entity.getRawJson());
      }
    };
  }

  @Override
  public Object insertTransaction(final TransactionRecord transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTransactionRecord.insert(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<TransactionRecord> transactions,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTransactionRecord.insert(transactions);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TransactionRecord>> getAllTransactions() {
    final String _sql = "SELECT * FROM transactions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionRecord>>() {
      @Override
      @NonNull
      public List<TransactionRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfAmountPaise = CursorUtil.getColumnIndexOrThrow(_cursor, "amount_paise");
          final int _cursorIndexOfPayerDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_device_id");
          final int _cursorIndexOfPayerAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_account_id");
          final int _cursorIndexOfPayeeDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_device_id");
          final int _cursorIndexOfPayeeAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_account_id");
          final int _cursorIndexOfDeviceCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "device_counter");
          final int _cursorIndexOfPrevTxHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_tx_hash");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "is_synced");
          final int _cursorIndexOfRawJson = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_json");
          final List<TransactionRecord> _result = new ArrayList<TransactionRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionRecord _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpAmountPaise;
            _tmpAmountPaise = _cursor.getLong(_cursorIndexOfAmountPaise);
            final String _tmpPayerDeviceId;
            _tmpPayerDeviceId = _cursor.getString(_cursorIndexOfPayerDeviceId);
            final String _tmpPayerAccountId;
            _tmpPayerAccountId = _cursor.getString(_cursorIndexOfPayerAccountId);
            final String _tmpPayeeDeviceId;
            _tmpPayeeDeviceId = _cursor.getString(_cursorIndexOfPayeeDeviceId);
            final String _tmpPayeeAccountId;
            _tmpPayeeAccountId = _cursor.getString(_cursorIndexOfPayeeAccountId);
            final long _tmpDeviceCounter;
            _tmpDeviceCounter = _cursor.getLong(_cursorIndexOfDeviceCounter);
            final String _tmpPrevTxHash;
            if (_cursor.isNull(_cursorIndexOfPrevTxHash)) {
              _tmpPrevTxHash = null;
            } else {
              _tmpPrevTxHash = _cursor.getString(_cursorIndexOfPrevTxHash);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            final String _tmpRawJson;
            _tmpRawJson = _cursor.getString(_cursorIndexOfRawJson);
            _item = new TransactionRecord(_tmpTxId,_tmpTokenId,_tmpAmountPaise,_tmpPayerDeviceId,_tmpPayerAccountId,_tmpPayeeDeviceId,_tmpPayeeAccountId,_tmpDeviceCounter,_tmpPrevTxHash,_tmpTimestamp,_tmpSignature,_tmpIsSynced,_tmpRawJson);
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

  @Override
  public Object getTransactionById(final String txId,
      final Continuation<? super TransactionRecord> $completion) {
    final String _sql = "SELECT * FROM transactions WHERE tx_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, txId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TransactionRecord>() {
      @Override
      @Nullable
      public TransactionRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfAmountPaise = CursorUtil.getColumnIndexOrThrow(_cursor, "amount_paise");
          final int _cursorIndexOfPayerDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_device_id");
          final int _cursorIndexOfPayerAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_account_id");
          final int _cursorIndexOfPayeeDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_device_id");
          final int _cursorIndexOfPayeeAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_account_id");
          final int _cursorIndexOfDeviceCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "device_counter");
          final int _cursorIndexOfPrevTxHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_tx_hash");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "is_synced");
          final int _cursorIndexOfRawJson = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_json");
          final TransactionRecord _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpAmountPaise;
            _tmpAmountPaise = _cursor.getLong(_cursorIndexOfAmountPaise);
            final String _tmpPayerDeviceId;
            _tmpPayerDeviceId = _cursor.getString(_cursorIndexOfPayerDeviceId);
            final String _tmpPayerAccountId;
            _tmpPayerAccountId = _cursor.getString(_cursorIndexOfPayerAccountId);
            final String _tmpPayeeDeviceId;
            _tmpPayeeDeviceId = _cursor.getString(_cursorIndexOfPayeeDeviceId);
            final String _tmpPayeeAccountId;
            _tmpPayeeAccountId = _cursor.getString(_cursorIndexOfPayeeAccountId);
            final long _tmpDeviceCounter;
            _tmpDeviceCounter = _cursor.getLong(_cursorIndexOfDeviceCounter);
            final String _tmpPrevTxHash;
            if (_cursor.isNull(_cursorIndexOfPrevTxHash)) {
              _tmpPrevTxHash = null;
            } else {
              _tmpPrevTxHash = _cursor.getString(_cursorIndexOfPrevTxHash);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            final String _tmpRawJson;
            _tmpRawJson = _cursor.getString(_cursorIndexOfRawJson);
            _result = new TransactionRecord(_tmpTxId,_tmpTokenId,_tmpAmountPaise,_tmpPayerDeviceId,_tmpPayerAccountId,_tmpPayeeDeviceId,_tmpPayeeAccountId,_tmpDeviceCounter,_tmpPrevTxHash,_tmpTimestamp,_tmpSignature,_tmpIsSynced,_tmpRawJson);
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
  public Object getUnsyncedTransactions(
      final Continuation<? super List<TransactionRecord>> $completion) {
    final String _sql = "SELECT * FROM transactions WHERE is_synced = 0 ORDER BY device_counter ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionRecord>>() {
      @Override
      @NonNull
      public List<TransactionRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfAmountPaise = CursorUtil.getColumnIndexOrThrow(_cursor, "amount_paise");
          final int _cursorIndexOfPayerDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_device_id");
          final int _cursorIndexOfPayerAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_account_id");
          final int _cursorIndexOfPayeeDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_device_id");
          final int _cursorIndexOfPayeeAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_account_id");
          final int _cursorIndexOfDeviceCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "device_counter");
          final int _cursorIndexOfPrevTxHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_tx_hash");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "is_synced");
          final int _cursorIndexOfRawJson = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_json");
          final List<TransactionRecord> _result = new ArrayList<TransactionRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionRecord _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpAmountPaise;
            _tmpAmountPaise = _cursor.getLong(_cursorIndexOfAmountPaise);
            final String _tmpPayerDeviceId;
            _tmpPayerDeviceId = _cursor.getString(_cursorIndexOfPayerDeviceId);
            final String _tmpPayerAccountId;
            _tmpPayerAccountId = _cursor.getString(_cursorIndexOfPayerAccountId);
            final String _tmpPayeeDeviceId;
            _tmpPayeeDeviceId = _cursor.getString(_cursorIndexOfPayeeDeviceId);
            final String _tmpPayeeAccountId;
            _tmpPayeeAccountId = _cursor.getString(_cursorIndexOfPayeeAccountId);
            final long _tmpDeviceCounter;
            _tmpDeviceCounter = _cursor.getLong(_cursorIndexOfDeviceCounter);
            final String _tmpPrevTxHash;
            if (_cursor.isNull(_cursorIndexOfPrevTxHash)) {
              _tmpPrevTxHash = null;
            } else {
              _tmpPrevTxHash = _cursor.getString(_cursorIndexOfPrevTxHash);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            final String _tmpRawJson;
            _tmpRawJson = _cursor.getString(_cursorIndexOfRawJson);
            _item = new TransactionRecord(_tmpTxId,_tmpTokenId,_tmpAmountPaise,_tmpPayerDeviceId,_tmpPayerAccountId,_tmpPayeeDeviceId,_tmpPayeeAccountId,_tmpDeviceCounter,_tmpPrevTxHash,_tmpTimestamp,_tmpSignature,_tmpIsSynced,_tmpRawJson);
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
  public Object getLatestTransaction(final Continuation<? super TransactionRecord> $completion) {
    final String _sql = "SELECT * FROM transactions ORDER BY device_counter DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TransactionRecord>() {
      @Override
      @Nullable
      public TransactionRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfAmountPaise = CursorUtil.getColumnIndexOrThrow(_cursor, "amount_paise");
          final int _cursorIndexOfPayerDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_device_id");
          final int _cursorIndexOfPayerAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payer_account_id");
          final int _cursorIndexOfPayeeDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_device_id");
          final int _cursorIndexOfPayeeAccountId = CursorUtil.getColumnIndexOrThrow(_cursor, "payee_account_id");
          final int _cursorIndexOfDeviceCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "device_counter");
          final int _cursorIndexOfPrevTxHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_tx_hash");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "is_synced");
          final int _cursorIndexOfRawJson = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_json");
          final TransactionRecord _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpAmountPaise;
            _tmpAmountPaise = _cursor.getLong(_cursorIndexOfAmountPaise);
            final String _tmpPayerDeviceId;
            _tmpPayerDeviceId = _cursor.getString(_cursorIndexOfPayerDeviceId);
            final String _tmpPayerAccountId;
            _tmpPayerAccountId = _cursor.getString(_cursorIndexOfPayerAccountId);
            final String _tmpPayeeDeviceId;
            _tmpPayeeDeviceId = _cursor.getString(_cursorIndexOfPayeeDeviceId);
            final String _tmpPayeeAccountId;
            _tmpPayeeAccountId = _cursor.getString(_cursorIndexOfPayeeAccountId);
            final long _tmpDeviceCounter;
            _tmpDeviceCounter = _cursor.getLong(_cursorIndexOfDeviceCounter);
            final String _tmpPrevTxHash;
            if (_cursor.isNull(_cursorIndexOfPrevTxHash)) {
              _tmpPrevTxHash = null;
            } else {
              _tmpPrevTxHash = _cursor.getString(_cursorIndexOfPrevTxHash);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            final String _tmpRawJson;
            _tmpRawJson = _cursor.getString(_cursorIndexOfRawJson);
            _result = new TransactionRecord(_tmpTxId,_tmpTokenId,_tmpAmountPaise,_tmpPayerDeviceId,_tmpPayerAccountId,_tmpPayeeDeviceId,_tmpPayeeAccountId,_tmpDeviceCounter,_tmpPrevTxHash,_tmpTimestamp,_tmpSignature,_tmpIsSynced,_tmpRawJson);
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
  public Object markSynced(final List<String> txIds, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE transactions SET is_synced = 1 WHERE tx_id IN (");
        final int _inputSize = txIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : txIds) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
