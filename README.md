# Database Migration Tool

This project is a Java-based GUI application for migrating databases. It provides a user-friendly interface to facilitate the migration process between source and target databases.

## Project Structure

- `src/main/java/com/brunner/db/migration/frmDBMigrator.java`: Contains the `frmDBMigrator` class, which extends `JFrame` and serves as the main GUI for the database migration tool. It includes methods for initializing the layout, handling button actions, and managing the migration process.

- `src/main/resources/config.json`: A JSON configuration file that contains settings for the database migration, such as source and target database information.

- `build.gradle`: The Gradle build configuration file that defines project dependencies, plugins, and tasks for building the project.

- `settings.gradle`: Configures the Gradle project settings, including the project name and included modules.

## Building the Project

To build the project, ensure that your `build.gradle` file contains the necessary tasks. A basic example of a `build.gradle` file for a Java project is as follows:

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.0'
}

tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}
```

You can run the build task using the command:

```
gradle build
```

This command compiles the Java files and packages the application. Make sure you have Gradle installed and properly configured on your system.

## Running the Application

After building the project, you can run the application by executing the main class `frmDBMigrator`. Ensure that the configuration file `config.json` is correctly set up with the necessary database connection details before running the application.