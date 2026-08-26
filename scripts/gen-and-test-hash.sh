#!/bin/bash
# Test si le hash en BDD matche le mot de passe
mkdir -p /tmp/bcrypt
cat > /tmp/bcrypt/Hash.java << 'JAVA'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class Hash {
  public static void main(String[] args) {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
    String pwd = args[0];
    String hash = args[1];
    System.out.println("MATCH:" + enc.matches(pwd, hash));
    System.out.println("FRESH:" + enc.encode(pwd));
  }
}
JAVA
cd /tmp/bcrypt
JAR=/home/azureuser/.m2/repository/org/springframework/security/spring-security-crypto/6.3.4/spring-security-crypto-6.3.4.jar
LOG=/home/azureuser/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
CP="$JAR:$LOG"
javac -cp "$CP" Hash.java
HASH_DB='$2a$10$We6TWhOE3UvkfFqfg9u4aO28/XLWsxru/gSfjBHBtQmQ4Z68E.lmy'
java -cp ".:$CP" Hash "President2025!" "$HASH_DB"
