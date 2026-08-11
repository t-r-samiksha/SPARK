package com.spark.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spark.wallet.data.dao.CachedCertDao
import com.spark.wallet.data.dao.CachedTrustDao
import com.spark.wallet.data.dao.LocalLedgerDao
import com.spark.wallet.data.dao.LocalPurseDao
import com.spark.wallet.data.dao.PendingRelayDao
import com.spark.wallet.data.dao.TransactionDao
import com.spark.wallet.data.entity.CachedCert
import com.spark.wallet.data.entity.CachedTrust
import com.spark.wallet.data.entity.LocalLedgerEntry
import com.spark.wallet.data.entity.LocalPurse
import com.spark.wallet.data.entity.PendingRelay
import com.spark.wallet.data.entity.TransactionRecord
import com.spark.wallet.security.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        LocalPurse::class,
        LocalLedgerEntry::class,
        CachedCert::class,
        CachedTrust::class,
        PendingRelay::class,
        TransactionRecord::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun purseDao(): LocalPurseDao
    abstract fun ledgerDao(): LocalLedgerDao
    abstract fun cachedCertDao(): CachedCertDao
    abstract fun cachedTrustDao(): CachedTrustDao
    abstract fun pendingRelayDao(): PendingRelayDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DB_NAME = "spark_wallet_encrypted.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration stub for future schema upgrades (v1 -> v2).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration strategy stub: executes DDL updates when transitioning from schema version 1 to 2
            }
        }

        /**
         * Retrieves or initializes the encrypted SQLCipher database, deriving the passphrase
         * from the hardware-backed Android KeyStore via [DatabaseKeyManager].
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val keyManager = DatabaseKeyManager(context.applicationContext)
                val passphrase = keyManager.getOrCreateDatabasePassphrase()
                getDatabase(context, passphrase)
            }
        }

        /**
         * Initializes the encrypted database with an explicit passphrase (e.g. for testing / custom key).
         */
        fun getDatabase(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
