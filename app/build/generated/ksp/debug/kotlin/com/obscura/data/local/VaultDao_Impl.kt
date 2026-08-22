package com.obscura.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class VaultDao_Impl(
  __db: RoomDatabase,
) : VaultDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfVaultEntity: EntityInsertAdapter<VaultEntity>

  private val __deleteAdapterOfVaultEntity: EntityDeleteOrUpdateAdapter<VaultEntity>

  private val __updateAdapterOfVaultEntity: EntityDeleteOrUpdateAdapter<VaultEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfVaultEntity = object : EntityInsertAdapter<VaultEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `vault_entries` (`id`,`title`,`category`,`usernameOrCardholder`,`secretValue`,`urlOrCardNumber`,`notesOrCvv`,`expiryDate`,`createdAt`,`updatedAt`,`lastAccessedAt`,`isFavorite`,`tags`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.usernameOrCardholder)
        statement.bindText(5, entity.secretValue)
        statement.bindText(6, entity.urlOrCardNumber)
        statement.bindText(7, entity.notesOrCvv)
        statement.bindText(8, entity.expiryDate)
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.updatedAt)
        statement.bindLong(11, entity.lastAccessedAt)
        val _tmp: Int = if (entity.isFavorite) 1 else 0
        statement.bindLong(12, _tmp.toLong())
        statement.bindText(13, entity.tags)
      }
    }
    this.__deleteAdapterOfVaultEntity = object : EntityDeleteOrUpdateAdapter<VaultEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `vault_entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfVaultEntity = object : EntityDeleteOrUpdateAdapter<VaultEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `vault_entries` SET `id` = ?,`title` = ?,`category` = ?,`usernameOrCardholder` = ?,`secretValue` = ?,`urlOrCardNumber` = ?,`notesOrCvv` = ?,`expiryDate` = ?,`createdAt` = ?,`updatedAt` = ?,`lastAccessedAt` = ?,`isFavorite` = ?,`tags` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.usernameOrCardholder)
        statement.bindText(5, entity.secretValue)
        statement.bindText(6, entity.urlOrCardNumber)
        statement.bindText(7, entity.notesOrCvv)
        statement.bindText(8, entity.expiryDate)
        statement.bindLong(9, entity.createdAt)
        statement.bindLong(10, entity.updatedAt)
        statement.bindLong(11, entity.lastAccessedAt)
        val _tmp: Int = if (entity.isFavorite) 1 else 0
        statement.bindLong(12, _tmp.toLong())
        statement.bindText(13, entity.tags)
        statement.bindText(14, entity.id)
      }
    }
  }

  public override suspend fun insertEntry(entry: VaultEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfVaultEntity.insert(_connection, entry)
  }

  public override suspend fun insertAll(entries: List<VaultEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfVaultEntity.insert(_connection, entries)
  }

  public override suspend fun deleteEntry(entry: VaultEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfVaultEntity.handle(_connection, entry)
  }

  public override suspend fun updateEntry(entry: VaultEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __updateAdapterOfVaultEntity.handle(_connection, entry)
  }

  public override fun getAllEntries(): Flow<List<VaultEntity>> {
    val _sql: String = "SELECT * FROM vault_entries ORDER BY isFavorite DESC, updatedAt DESC"
    return createFlow(__db, false, arrayOf("vault_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUsernameOrCardholder: Int = getColumnIndexOrThrow(_stmt,
            "usernameOrCardholder")
        val _columnIndexOfSecretValue: Int = getColumnIndexOrThrow(_stmt, "secretValue")
        val _columnIndexOfUrlOrCardNumber: Int = getColumnIndexOrThrow(_stmt, "urlOrCardNumber")
        val _columnIndexOfNotesOrCvv: Int = getColumnIndexOrThrow(_stmt, "notesOrCvv")
        val _columnIndexOfExpiryDate: Int = getColumnIndexOrThrow(_stmt, "expiryDate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfLastAccessedAt: Int = getColumnIndexOrThrow(_stmt, "lastAccessedAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<VaultEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VaultEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpUsernameOrCardholder: String
          _tmpUsernameOrCardholder = _stmt.getText(_columnIndexOfUsernameOrCardholder)
          val _tmpSecretValue: String
          _tmpSecretValue = _stmt.getText(_columnIndexOfSecretValue)
          val _tmpUrlOrCardNumber: String
          _tmpUrlOrCardNumber = _stmt.getText(_columnIndexOfUrlOrCardNumber)
          val _tmpNotesOrCvv: String
          _tmpNotesOrCvv = _stmt.getText(_columnIndexOfNotesOrCvv)
          val _tmpExpiryDate: String
          _tmpExpiryDate = _stmt.getText(_columnIndexOfExpiryDate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpLastAccessedAt: Long
          _tmpLastAccessedAt = _stmt.getLong(_columnIndexOfLastAccessedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item =
              VaultEntity(_tmpId,_tmpTitle,_tmpCategory,_tmpUsernameOrCardholder,_tmpSecretValue,_tmpUrlOrCardNumber,_tmpNotesOrCvv,_tmpExpiryDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastAccessedAt,_tmpIsFavorite,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEntriesByCategory(category: String): Flow<List<VaultEntity>> {
    val _sql: String =
        "SELECT * FROM vault_entries WHERE category = ? ORDER BY isFavorite DESC, updatedAt DESC"
    return createFlow(__db, false, arrayOf("vault_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, category)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUsernameOrCardholder: Int = getColumnIndexOrThrow(_stmt,
            "usernameOrCardholder")
        val _columnIndexOfSecretValue: Int = getColumnIndexOrThrow(_stmt, "secretValue")
        val _columnIndexOfUrlOrCardNumber: Int = getColumnIndexOrThrow(_stmt, "urlOrCardNumber")
        val _columnIndexOfNotesOrCvv: Int = getColumnIndexOrThrow(_stmt, "notesOrCvv")
        val _columnIndexOfExpiryDate: Int = getColumnIndexOrThrow(_stmt, "expiryDate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfLastAccessedAt: Int = getColumnIndexOrThrow(_stmt, "lastAccessedAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<VaultEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VaultEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpUsernameOrCardholder: String
          _tmpUsernameOrCardholder = _stmt.getText(_columnIndexOfUsernameOrCardholder)
          val _tmpSecretValue: String
          _tmpSecretValue = _stmt.getText(_columnIndexOfSecretValue)
          val _tmpUrlOrCardNumber: String
          _tmpUrlOrCardNumber = _stmt.getText(_columnIndexOfUrlOrCardNumber)
          val _tmpNotesOrCvv: String
          _tmpNotesOrCvv = _stmt.getText(_columnIndexOfNotesOrCvv)
          val _tmpExpiryDate: String
          _tmpExpiryDate = _stmt.getText(_columnIndexOfExpiryDate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpLastAccessedAt: Long
          _tmpLastAccessedAt = _stmt.getLong(_columnIndexOfLastAccessedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item =
              VaultEntity(_tmpId,_tmpTitle,_tmpCategory,_tmpUsernameOrCardholder,_tmpSecretValue,_tmpUrlOrCardNumber,_tmpNotesOrCvv,_tmpExpiryDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastAccessedAt,_tmpIsFavorite,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun searchEntries(query: String): Flow<List<VaultEntity>> {
    val _sql: String = """
        |
        |        SELECT * FROM vault_entries 
        |        WHERE title LIKE '%' || ? || '%' 
        |           OR usernameOrCardholder LIKE '%' || ? || '%' 
        |           OR tags LIKE '%' || ? || '%'
        |        ORDER BY isFavorite DESC, updatedAt DESC
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("vault_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        _argIndex = 3
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUsernameOrCardholder: Int = getColumnIndexOrThrow(_stmt,
            "usernameOrCardholder")
        val _columnIndexOfSecretValue: Int = getColumnIndexOrThrow(_stmt, "secretValue")
        val _columnIndexOfUrlOrCardNumber: Int = getColumnIndexOrThrow(_stmt, "urlOrCardNumber")
        val _columnIndexOfNotesOrCvv: Int = getColumnIndexOrThrow(_stmt, "notesOrCvv")
        val _columnIndexOfExpiryDate: Int = getColumnIndexOrThrow(_stmt, "expiryDate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfLastAccessedAt: Int = getColumnIndexOrThrow(_stmt, "lastAccessedAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<VaultEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VaultEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpUsernameOrCardholder: String
          _tmpUsernameOrCardholder = _stmt.getText(_columnIndexOfUsernameOrCardholder)
          val _tmpSecretValue: String
          _tmpSecretValue = _stmt.getText(_columnIndexOfSecretValue)
          val _tmpUrlOrCardNumber: String
          _tmpUrlOrCardNumber = _stmt.getText(_columnIndexOfUrlOrCardNumber)
          val _tmpNotesOrCvv: String
          _tmpNotesOrCvv = _stmt.getText(_columnIndexOfNotesOrCvv)
          val _tmpExpiryDate: String
          _tmpExpiryDate = _stmt.getText(_columnIndexOfExpiryDate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpLastAccessedAt: Long
          _tmpLastAccessedAt = _stmt.getLong(_columnIndexOfLastAccessedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item =
              VaultEntity(_tmpId,_tmpTitle,_tmpCategory,_tmpUsernameOrCardholder,_tmpSecretValue,_tmpUrlOrCardNumber,_tmpNotesOrCvv,_tmpExpiryDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastAccessedAt,_tmpIsFavorite,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getEntryById(id: String): VaultEntity? {
    val _sql: String = "SELECT * FROM vault_entries WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUsernameOrCardholder: Int = getColumnIndexOrThrow(_stmt,
            "usernameOrCardholder")
        val _columnIndexOfSecretValue: Int = getColumnIndexOrThrow(_stmt, "secretValue")
        val _columnIndexOfUrlOrCardNumber: Int = getColumnIndexOrThrow(_stmt, "urlOrCardNumber")
        val _columnIndexOfNotesOrCvv: Int = getColumnIndexOrThrow(_stmt, "notesOrCvv")
        val _columnIndexOfExpiryDate: Int = getColumnIndexOrThrow(_stmt, "expiryDate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfLastAccessedAt: Int = getColumnIndexOrThrow(_stmt, "lastAccessedAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: VaultEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpUsernameOrCardholder: String
          _tmpUsernameOrCardholder = _stmt.getText(_columnIndexOfUsernameOrCardholder)
          val _tmpSecretValue: String
          _tmpSecretValue = _stmt.getText(_columnIndexOfSecretValue)
          val _tmpUrlOrCardNumber: String
          _tmpUrlOrCardNumber = _stmt.getText(_columnIndexOfUrlOrCardNumber)
          val _tmpNotesOrCvv: String
          _tmpNotesOrCvv = _stmt.getText(_columnIndexOfNotesOrCvv)
          val _tmpExpiryDate: String
          _tmpExpiryDate = _stmt.getText(_columnIndexOfExpiryDate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpLastAccessedAt: Long
          _tmpLastAccessedAt = _stmt.getLong(_columnIndexOfLastAccessedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _result =
              VaultEntity(_tmpId,_tmpTitle,_tmpCategory,_tmpUsernameOrCardholder,_tmpSecretValue,_tmpUrlOrCardNumber,_tmpNotesOrCvv,_tmpExpiryDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastAccessedAt,_tmpIsFavorite,_tmpTags)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEntriesCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM vault_entries"
    return createFlow(__db, false, arrayOf("vault_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllEntriesDirect(): List<VaultEntity> {
    val _sql: String = "SELECT * FROM vault_entries"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfUsernameOrCardholder: Int = getColumnIndexOrThrow(_stmt,
            "usernameOrCardholder")
        val _columnIndexOfSecretValue: Int = getColumnIndexOrThrow(_stmt, "secretValue")
        val _columnIndexOfUrlOrCardNumber: Int = getColumnIndexOrThrow(_stmt, "urlOrCardNumber")
        val _columnIndexOfNotesOrCvv: Int = getColumnIndexOrThrow(_stmt, "notesOrCvv")
        val _columnIndexOfExpiryDate: Int = getColumnIndexOrThrow(_stmt, "expiryDate")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfLastAccessedAt: Int = getColumnIndexOrThrow(_stmt, "lastAccessedAt")
        val _columnIndexOfIsFavorite: Int = getColumnIndexOrThrow(_stmt, "isFavorite")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _result: MutableList<VaultEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: VaultEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpUsernameOrCardholder: String
          _tmpUsernameOrCardholder = _stmt.getText(_columnIndexOfUsernameOrCardholder)
          val _tmpSecretValue: String
          _tmpSecretValue = _stmt.getText(_columnIndexOfSecretValue)
          val _tmpUrlOrCardNumber: String
          _tmpUrlOrCardNumber = _stmt.getText(_columnIndexOfUrlOrCardNumber)
          val _tmpNotesOrCvv: String
          _tmpNotesOrCvv = _stmt.getText(_columnIndexOfNotesOrCvv)
          val _tmpExpiryDate: String
          _tmpExpiryDate = _stmt.getText(_columnIndexOfExpiryDate)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmpLastAccessedAt: Long
          _tmpLastAccessedAt = _stmt.getLong(_columnIndexOfLastAccessedAt)
          val _tmpIsFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFavorite).toInt()
          _tmpIsFavorite = _tmp != 0
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          _item =
              VaultEntity(_tmpId,_tmpTitle,_tmpCategory,_tmpUsernameOrCardholder,_tmpSecretValue,_tmpUrlOrCardNumber,_tmpNotesOrCvv,_tmpExpiryDate,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastAccessedAt,_tmpIsFavorite,_tmpTags)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteEntryById(id: String) {
    val _sql: String = "DELETE FROM vault_entries WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateFavoriteStatus(
    id: String,
    isFavorite: Boolean,
    timestamp: Long,
  ) {
    val _sql: String = "UPDATE vault_entries SET isFavorite = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isFavorite) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, timestamp)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM vault_entries"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
