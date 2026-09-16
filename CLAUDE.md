# Obscura Password Manager

Android, Kotlin + Jetpack Compose. Пакет `com.obscura`. Тема `ObscuraTheme`.
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

## Манифест

### Ориентация

Портретная ориентация фиксирована для всех Activity
(`android:screenOrientation="portrait"`): в ландшафте клавиатура PIN не
помещается на экран. Новые Activity обязаны объявлять portrait — это
проверяет инструментальный тест `everyActivityIsPortraitOnly`.

На API 26 полупрозрачное окно с фиксированной ориентацией падает в
`Activity.onCreate` с `IllegalStateException: Only fullscreen opaque
activities can request orientation` (при targetSdk > 26; в 8.1 проверку
убрали). Поэтому `AutofillBiometricAuthActivity` на API 26 делается
непрозрачной через `values-v26` / `values-v27`. Проверено на эмуляторе
API 26 тестом `AutofillBiometricAuthActivityTest`, включая контрольный
прогон: без этих ресурсов тест падает именно с этим исключением.

Android 16 (targetSdk 36) на больших экранах игнорирует `screenOrientation`
(ChangeId `UNIVERSAL_RESIZABLE_BY_DEFAULT`; порог ≥ 600dp — по документации).
На планшетах фиксация не работает — поведение экрана входа в ландшафте там
не проверено.

### Разрешения в APK

Фактический список (`aapt2 dump permissions` release APK):

- `USE_BIOMETRIC`
- `USE_FINGERPRINT` (maxSdkVersion 28)
- `PROVIDE_CREDENTIALS`
- `com.obscura.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` от androidx.core —
  signature-уровня, объявлено и запрошено (две записи).

`INTERNET` нет и не должно появиться: приложение офлайновое. После
изменения зависимостей проверяй список заново.

AutofillService в приложении пока нет, `AutofillBiometricAuthActivity`
ниоткуда не запускается. Когда сервис появится, он защищается атрибутом
`android:permission="android.permission.BIND_AUTOFILL_SERVICE"` у `<service>`,
это не `uses-permission`.

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

## Синхронизация с кодом

Числа в этом файле (итерации KDF, номера версий форматов) — часть
контракта совместимости. При расхождении с кодом не подгоняй код под
файл и наоборот молча: сообщи о расхождении и спроси.

## Слияние бэкапов

При «Объединить» побеждает большее `updatedAt`, при равенстве — запись из
хранилища. `updatedAt` берётся с часов устройства: при сильном расхождении
часов между устройствами «более свежей» окажется запись с того, где часы
убежали вперёд. Это принятое ограничение — бэкап здесь восстановление, а не
синхронизация. Не вводить векторные часы без реальной задачи синка.