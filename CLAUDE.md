# Obscura Password Manager

Android, Kotlin + Jetpack Compose. Пакет `com.obscura`. Тема `MyApplicationTheme`.
minSdk 24, targetSdk 36, compileSdk 37.

## Архитектура ключей — не менять без обсуждения

DEK (AES-256, случайный) — единственный ключ хранилища. На диск в открытом
виде не попадает никогда. Две обёртки:

- PIN: KEK = HMAC(keystore_pepper, PBKDF2(pin, salt, 210k)) → pin_wrapped_dek
- Биометрия: Keystore-ключ с setUserAuthenticationRequired → bio_wrapped_dek

Keystore-pepper (HMAC-SHA256, non-exportable) делает офлайн-перебор PIN
невозможным без физического устройства. Это и есть основная защита, а не
число итераций PBKDF2.

Ключ базы = HKDF-SHA256(DEK, info="obscura-db-key-v1") → SQLCipher raw
`x'<64 hex>'`. DEK напрямую в SQLCipher не передаётся.

### Ключ бэкапа НАМЕРЕННО устроен иначе

`BackupCryptoUtils` выводит ключ чистым PBKDF2(пароль, соль, 600k), без
Keystore и без DEK. Это выглядит непоследовательно на фоне `DatabaseKey`,
но менять нельзя: DEK привязан к Keystore конкретного устройства, а бэкап
восстанавливают на новом телефоне. Ключ бэкапа обязан быть переносимым.
Не «унифицировать» с HKDF от DEK ни при каких обстоятельствах.

## Правила работы с базой

- Инстанс `VaultDatabase` создаётся при разблокировке и закрывается при
  `lock()`. Глобального синглтона нет и быть не должно.
- Любое обращение к DAO — только через `VaultSession.runInSession`,
  `launchInSession` или `observe`. Не через `viewModelScope`.
- Многошаговые записи — через `VaultSession.runInTransaction`.
- `lock()`: stop accepting → `cancelAndJoin()` → `close()`, под `NonCancellable`.
- SQLCipher 4.19.0 не обнуляет переданный массив ключа и читает его заново
  при каждом новом соединении. Стирать только после `super.close()`.
- `System.loadLibrary("sqlcipher")` вызывается явно в `ObscuraApplication`.

## Формат файла бэкапа

`OBVB`(4) | formatVersion(2) | соль(16) | IV(12) | AES-256-GCM+тег.
Заголовок открытый и передаётся в GCM как AAD. Три разных исключения:
`UnsupportedBackupVersionException`, `BackupFormatException`, `SecurityException`.

## Правила для тебя

- Перед изменением криптографии сверяй примитивы с внешним эталоном
  (OpenSSL), а не только со своими тестами.
- Утверждения про поведение библиотек проверяй по байткоду или исходникам,
  не по памяти и не по аналогии с прошлой версией.
- Криптография и жизненный цикл ключей проверяются инструментальными
  тестами на эмуляторе. JVM-тесты на фейковой базе проверяют твою модель
  Room, а не Room.
- Коммить после каждой завершённой задачи.
- Не расширяй видимость `internal` ради тестов.
- Путь проекта не должен содержать запятых: `-Xfriend-paths` разбивается
  по запятой, и тесты перестают видеть `internal`.
