#!/bin/bash
# Génère un hash BCrypt pour un mot de passe donné
# Usage: ./gen-hash.sh <password>
PWD="${1:-Test1234!}"

mkdir -p /tmp/bcrypt
cat > /tmp/bcrypt/Hash.java << 'JAVA'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class Hash {
  public static void main(String[] args) {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
    System.out.println(enc.encode(args[0]));
  }
}
JAVA

cd /tmp/bcrypt
JAR=/home/azureuser/.m2/repository/org/springframework/security/spring-security-crypto/6.3.4/spring-security-crypto-6.3.4.jar
LOG=/home/azureuser/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar
CP="$JAR:$LOG"
javac -cp "$CP" Hash.java
HASH=$(java -cp ".:$CP" Hash "$PWD")
echo "$HASH"
