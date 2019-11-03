# Postgres Notify Flowable

Consume Postgres LISTEN/NOTIFY messages in your JVM applications using RxJava, and the standard Postgres JDBC driver.

- Enables RxJava's rich API for asynchronous processing
- Requires only a single database connection for as many subscribers as you want
- Uses standard Postgres JDBC driver
- Minimizes polling overhead by centralizing it

## About

The Postgres JDBC driver has built in support for retrieving LISTEN/NOTIFY messages. The provided API is pretty basic. This code is an adapter from this basic API to RxJava to allow for a much richer asynchronous API.

Further, Postgres requires a dedicated connection to call LISTEN on. One has to be careful not to be too wasteful with database connections.  This code enables you to ergonomically share a single such connection for multiple usages in your application by making use of the reactive extensions `share()` functionality.

The idea is that you create a single `PostgresNotifyFlowable`, providing it all channels you would like to LISTEN on, and then you can subscribe to this `Flowable` as many times as you want sharing a single database connection across your application.

The standard Postgres JDBC driver API is built around polling to retrieve LISTEN/NOTIFY messages. This might be wasteful in some scenarios, but at the same time one might not want to forgo the advantages of using the standard driver over a non-standard non-polling based one. This code helps you minimize the overhead by making it simple to have centralized polling over a single connection.


## Setup

Add [JitPack](https://jitpack.io/) to your repositories:
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add the dependency:

```groovy
dependencies {
    implementation 'com.github.ayedo:PostgresNotifyFlowable:v1.2.0'
}
```

## How to use

### Kotlin
```kotlin
val channels = PostgresNotifyFlowable.forChannels(
    jdbcUrl = db.jdbcUrl,
    user = db.username,
    password = db.password,
    channels = listOf("test"))

channels
    .filter({ it.name == "test" })
    .subscribe({ notification: PGNotification ->
        println("${it.name} ${it.parameter}")
    })
```
### Java
```java
Flowable<PGNotification> notifications = PostgresNotifyFlowable.INSTANCE.forChannels("url", "user", "password", List.of("test"));
notifications.filter(notification -> notification.getName().equals("test"))
        .subscribe(notification -> System.out.println(notification.getName() + " " + notification.getParameter()));
```
