# Default recipe: list available recipes
default:
    @just --list

# Build the library
build:
    ./gradlew build

# Run tests
test:
    ./gradlew test

# Clean build artifacts
clean:
    ./gradlew clean

# Publish to local Maven repository (~/.m2)
publish-local:
    ./gradlew publishToMavenLocal
    echo "\033[32mPublished to local Maven repository\033[0m"
    group=$(./gradlew properties -q 2>/dev/null | grep '^group:' | awk '{print $2}')
    artifact=$(./gradlew properties -q 2>/dev/null | grep '^name:' | awk '{print $2}')
    version=$(./gradlew properties -q 2>/dev/null | grep '^version:' | awk '{print $2}')
    echo "  Group:    $group"
    echo "  Artifact: $artifact"
    echo "  Version:  $version"
