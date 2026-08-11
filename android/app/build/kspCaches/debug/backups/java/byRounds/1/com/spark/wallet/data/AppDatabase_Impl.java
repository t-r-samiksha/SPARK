package com.spark.wallet.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.spark.wallet.data.dao.CachedCertDao;
import com.spark.wallet.data.dao.CachedCertDao_Impl;
import com.spark.wallet.data.dao.CachedTrustDao;
import com.spark.wallet.data.dao.CachedTrustDao_Impl;
import com.spark.wallet.data.dao.LocalLedgerDao;
import com.spark.wallet.data.dao.LocalLedgerDao_Impl;
import com.spark.wallet.data.dao.LocalPurseDao;
import com.spark.wallet.data.dao.LocalPurseDao_Impl;
import com.spark.wallet.data.dao.PendingRelayDao;
import com.spark.wallet.data.dao.PendingRelayDao_Impl;
import com.spark.wallet.data.dao.TransactionDao;
import com.spark.wallet.data.dao.TransactionDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile LocalPurseDao _localPurseDao;

  private volatile LocalLedgerDao _localLedgerDao;

  private volatile CachedCertDao _cachedCertDao;

  private volatile CachedTrustDao _cachedTrustDao;

  private volatile PendingRelayDao _pendingRelayDao;

  private volatile TransactionDao _transactionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `local_purse` (`token_id` TEXT NOT NULL, `cap` INTEGER NOT NULL, `remaining` INTEGER NOT NULL, `counter_current` INTEGER NOT NULL, `signed_token_blob` TEXT NOT NULL, `expires_at` INTEGER NOT NULL, PRIMARY KEY(`token_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `local_ledger` (`tx_id` TEXT NOT NULL, `direction` TEXT NOT NULL, `counterparty_id` TEXT NOT NULL, `amount` INTEGER NOT NULL, `counter` INTEGER NOT NULL, `prev_hash` TEXT, `hash` TEXT NOT NULL, `signature` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`tx_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cached_certs` (`device_id` TEXT NOT NULL, `public_key` TEXT NOT NULL, `cert_blob` TEXT NOT NULL, `expires_at` INTEGER NOT NULL, PRIMARY KEY(`device_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cached_trust` (`subject_id` TEXT NOT NULL, `trust_score` REAL NOT NULL, `attestation_blobs` TEXT NOT NULL, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`subject_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_relay` (`tx_id` TEXT NOT NULL, `blob` TEXT NOT NULL, `destination_hint` TEXT, `ttl` INTEGER NOT NULL, `received_at` INTEGER NOT NULL, PRIMARY KEY(`tx_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`tx_id` TEXT NOT NULL, `token_id` TEXT NOT NULL, `amount_paise` INTEGER NOT NULL, `payer_device_id` TEXT NOT NULL, `payer_account_id` TEXT NOT NULL, `payee_device_id` TEXT NOT NULL, `payee_account_id` TEXT NOT NULL, `device_counter` INTEGER NOT NULL, `prev_tx_hash` TEXT, `timestamp` INTEGER NOT NULL, `signature` TEXT NOT NULL, `is_synced` INTEGER NOT NULL, `raw_json` TEXT NOT NULL, PRIMARY KEY(`tx_id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1360d32d8ea9b35a6b1c3de65446af64')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `local_purse`");
        db.execSQL("DROP TABLE IF EXISTS `local_ledger`");
        db.execSQL("DROP TABLE IF EXISTS `cached_certs`");
        db.execSQL("DROP TABLE IF EXISTS `cached_trust`");
        db.execSQL("DROP TABLE IF EXISTS `pending_relay`");
        db.execSQL("DROP TABLE IF EXISTS `transactions`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsLocalPurse = new HashMap<String, TableInfo.Column>(6);
        _columnsLocalPurse.put("token_id", new TableInfo.Column("token_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalPurse.put("cap", new TableInfo.Column("cap", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalPurse.put("remaining", new TableInfo.Column("remaining", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalPurse.put("counter_current", new TableInfo.Column("counter_current", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalPurse.put("signed_token_blob", new TableInfo.Column("signed_token_blob", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalPurse.put("expires_at", new TableInfo.Column("expires_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLocalPurse = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLocalPurse = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoLocalPurse = new TableInfo("local_purse", _columnsLocalPurse, _foreignKeysLocalPurse, _indicesLocalPurse);
        final TableInfo _existingLocalPurse = TableInfo.read(db, "local_purse");
        if (!_infoLocalPurse.equals(_existingLocalPurse)) {
          return new RoomOpenHelper.ValidationResult(false, "local_purse(com.spark.wallet.data.entity.LocalPurse).\n"
                  + " Expected:\n" + _infoLocalPurse + "\n"
                  + " Found:\n" + _existingLocalPurse);
        }
        final HashMap<String, TableInfo.Column> _columnsLocalLedger = new HashMap<String, TableInfo.Column>(10);
        _columnsLocalLedger.put("tx_id", new TableInfo.Column("tx_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("direction", new TableInfo.Column("direction", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("counterparty_id", new TableInfo.Column("counterparty_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("amount", new TableInfo.Column("amount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("counter", new TableInfo.Column("counter", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("prev_hash", new TableInfo.Column("prev_hash", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("hash", new TableInfo.Column("hash", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("signature", new TableInfo.Column("signature", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalLedger.put("synced", new TableInfo.Column("synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLocalLedger = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLocalLedger = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoLocalLedger = new TableInfo("local_ledger", _columnsLocalLedger, _foreignKeysLocalLedger, _indicesLocalLedger);
        final TableInfo _existingLocalLedger = TableInfo.read(db, "local_ledger");
        if (!_infoLocalLedger.equals(_existingLocalLedger)) {
          return new RoomOpenHelper.ValidationResult(false, "local_ledger(com.spark.wallet.data.entity.LocalLedgerEntry).\n"
                  + " Expected:\n" + _infoLocalLedger + "\n"
                  + " Found:\n" + _existingLocalLedger);
        }
        final HashMap<String, TableInfo.Column> _columnsCachedCerts = new HashMap<String, TableInfo.Column>(4);
        _columnsCachedCerts.put("device_id", new TableInfo.Column("device_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedCerts.put("public_key", new TableInfo.Column("public_key", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedCerts.put("cert_blob", new TableInfo.Column("cert_blob", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedCerts.put("expires_at", new TableInfo.Column("expires_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCachedCerts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCachedCerts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCachedCerts = new TableInfo("cached_certs", _columnsCachedCerts, _foreignKeysCachedCerts, _indicesCachedCerts);
        final TableInfo _existingCachedCerts = TableInfo.read(db, "cached_certs");
        if (!_infoCachedCerts.equals(_existingCachedCerts)) {
          return new RoomOpenHelper.ValidationResult(false, "cached_certs(com.spark.wallet.data.entity.CachedCert).\n"
                  + " Expected:\n" + _infoCachedCerts + "\n"
                  + " Found:\n" + _existingCachedCerts);
        }
        final HashMap<String, TableInfo.Column> _columnsCachedTrust = new HashMap<String, TableInfo.Column>(4);
        _columnsCachedTrust.put("subject_id", new TableInfo.Column("subject_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedTrust.put("trust_score", new TableInfo.Column("trust_score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedTrust.put("attestation_blobs", new TableInfo.Column("attestation_blobs", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCachedTrust.put("cached_at", new TableInfo.Column("cached_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCachedTrust = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCachedTrust = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCachedTrust = new TableInfo("cached_trust", _columnsCachedTrust, _foreignKeysCachedTrust, _indicesCachedTrust);
        final TableInfo _existingCachedTrust = TableInfo.read(db, "cached_trust");
        if (!_infoCachedTrust.equals(_existingCachedTrust)) {
          return new RoomOpenHelper.ValidationResult(false, "cached_trust(com.spark.wallet.data.entity.CachedTrust).\n"
                  + " Expected:\n" + _infoCachedTrust + "\n"
                  + " Found:\n" + _existingCachedTrust);
        }
        final HashMap<String, TableInfo.Column> _columnsPendingRelay = new HashMap<String, TableInfo.Column>(5);
        _columnsPendingRelay.put("tx_id", new TableInfo.Column("tx_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingRelay.put("blob", new TableInfo.Column("blob", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingRelay.put("destination_hint", new TableInfo.Column("destination_hint", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingRelay.put("ttl", new TableInfo.Column("ttl", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingRelay.put("received_at", new TableInfo.Column("received_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPendingRelay = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPendingRelay = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPendingRelay = new TableInfo("pending_relay", _columnsPendingRelay, _foreignKeysPendingRelay, _indicesPendingRelay);
        final TableInfo _existingPendingRelay = TableInfo.read(db, "pending_relay");
        if (!_infoPendingRelay.equals(_existingPendingRelay)) {
          return new RoomOpenHelper.ValidationResult(false, "pending_relay(com.spark.wallet.data.entity.PendingRelay).\n"
                  + " Expected:\n" + _infoPendingRelay + "\n"
                  + " Found:\n" + _existingPendingRelay);
        }
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(13);
        _columnsTransactions.put("tx_id", new TableInfo.Column("tx_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("token_id", new TableInfo.Column("token_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount_paise", new TableInfo.Column("amount_paise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("payer_device_id", new TableInfo.Column("payer_device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("payer_account_id", new TableInfo.Column("payer_account_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("payee_device_id", new TableInfo.Column("payee_device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("payee_account_id", new TableInfo.Column("payee_account_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("device_counter", new TableInfo.Column("device_counter", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("prev_tx_hash", new TableInfo.Column("prev_tx_hash", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("signature", new TableInfo.Column("signature", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("is_synced", new TableInfo.Column("is_synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("raw_json", new TableInfo.Column("raw_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(db, "transactions");
        if (!_infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.spark.wallet.data.entity.TransactionRecord).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1360d32d8ea9b35a6b1c3de65446af64", "429ffb7b49eed66741aa01fb3568dbe1");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "local_purse","local_ledger","cached_certs","cached_trust","pending_relay","transactions");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `local_purse`");
      _db.execSQL("DELETE FROM `local_ledger`");
      _db.execSQL("DELETE FROM `cached_certs`");
      _db.execSQL("DELETE FROM `cached_trust`");
      _db.execSQL("DELETE FROM `pending_relay`");
      _db.execSQL("DELETE FROM `transactions`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(LocalPurseDao.class, LocalPurseDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LocalLedgerDao.class, LocalLedgerDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CachedCertDao.class, CachedCertDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CachedTrustDao.class, CachedTrustDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PendingRelayDao.class, PendingRelayDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public LocalPurseDao purseDao() {
    if (_localPurseDao != null) {
      return _localPurseDao;
    } else {
      synchronized(this) {
        if(_localPurseDao == null) {
          _localPurseDao = new LocalPurseDao_Impl(this);
        }
        return _localPurseDao;
      }
    }
  }

  @Override
  public LocalLedgerDao ledgerDao() {
    if (_localLedgerDao != null) {
      return _localLedgerDao;
    } else {
      synchronized(this) {
        if(_localLedgerDao == null) {
          _localLedgerDao = new LocalLedgerDao_Impl(this);
        }
        return _localLedgerDao;
      }
    }
  }

  @Override
  public CachedCertDao cachedCertDao() {
    if (_cachedCertDao != null) {
      return _cachedCertDao;
    } else {
      synchronized(this) {
        if(_cachedCertDao == null) {
          _cachedCertDao = new CachedCertDao_Impl(this);
        }
        return _cachedCertDao;
      }
    }
  }

  @Override
  public CachedTrustDao cachedTrustDao() {
    if (_cachedTrustDao != null) {
      return _cachedTrustDao;
    } else {
      synchronized(this) {
        if(_cachedTrustDao == null) {
          _cachedTrustDao = new CachedTrustDao_Impl(this);
        }
        return _cachedTrustDao;
      }
    }
  }

  @Override
  public PendingRelayDao pendingRelayDao() {
    if (_pendingRelayDao != null) {
      return _pendingRelayDao;
    } else {
      synchronized(this) {
        if(_pendingRelayDao == null) {
          _pendingRelayDao = new PendingRelayDao_Impl(this);
        }
        return _pendingRelayDao;
      }
    }
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }
}
