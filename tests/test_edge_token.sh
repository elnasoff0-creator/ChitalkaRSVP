#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/TokenTest.java" <<'JAVA'
package ru.reader.rsvp;
public final class TokenTest {
    public static void main(String[] args) {
        String expected="2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2";
        String actual=EdgeToken.generate(1700000000L);
        if (!expected.equals(actual)) throw new AssertionError("Edge GEC v2 token mismatch: "+actual);
        if (actual.length()!=81) throw new AssertionError("Edge token length");
        System.out.println("Edge GEC token v2 vector: PASS");
    }
}
JAVA
javac -d "$TMP" app/src/main/java/ru/reader/rsvp/EdgeToken.java "$TMP/TokenTest.java"
java -cp "$TMP" ru.reader.rsvp.TokenTest
