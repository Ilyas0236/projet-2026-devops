#!/bin/bash
# Générer un hash BCrypt via Java directement dans le conteneur auth-service
# En utilisant la classe BCryptPasswordEncoder de Spring Security
# On peut aussi utiliser l'utilitaire htpasswd / python / openssl
# Le plus simple : utiliser le jar Spring Security inclus dans le conteneur

# Méthode 1 : via Python (crypt ne supporte pas $2b$ nativement, mais passlib oui)
# Méthode 2 : utiliser la CLI Java avec les jars Spring Security du conteneur

# Optons pour une approche simple : interroger le conteneur auth-service qui a les jars Spring
echo "=== Génération d'un hash BCrypt via Java dans le conteneur auth-service ==="

# On cherche un jar Spring Security dans le conteneur
docker exec wydad-auth-service sh -c '
  # Cherchons un jar contenant BCryptPasswordEncoder
  JAR=$(find / -name "spring-security-crypto*.jar" 2>/dev/null | head -1)
  echo "JAR: $JAR"
  if [ -n "$JAR" ]; then
    # Crée un petit programme Java
    cat > /tmp/Hash.java << "EOF"
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class Hash {
  public static void main(String[] args) {
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
    String pwd = args[0];
    System.out.println(enc.encode(pwd));
    System.out.println("MATCH:" + enc.matches(pwd, args[1]));
  }
}
EOF
    javac -cp $JAR /tmp/Hash.java
    java -cp /tmp:$JAR Hash "President2025!" "\$2a\$10\$2Q3pE3lZTQYYjJi7tM6cE.6sGvO2xN3Tkj3z5pT0k5NQqGqJYB1h2"
  fi
' 2>&1 | head -30
