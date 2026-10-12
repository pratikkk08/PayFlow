package com.payflow.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RsaKeyService {

	private static final Logger log = LoggerFactory.getLogger(RsaKeyService.class);

	private final PayflowSecurityProperties properties;
	private RSAPrivateKey privateKey;
	private RSAPublicKey publicKey;

	public RsaKeyService(PayflowSecurityProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void init() {
		Path privatePath = Path.of(properties.getJwt().getPrivateKeyPath()).toAbsolutePath().normalize();
		Path publicPath = Path.of(properties.getJwt().getPublicKeyPath()).toAbsolutePath().normalize();

		try {
			if (Files.exists(privatePath) && Files.exists(publicPath)) {
				this.privateKey = readPrivateKey(privatePath);
				this.publicKey = readPublicKey(publicPath);
				log.info("Loaded JWT RSA key pair from {}", privatePath.getParent());
				return;
			}

			if (!properties.getJwt().isAutoGenerateKeys()) {
				throw new IllegalStateException(
						"JWT RSA keys not found at " + privatePath + " / " + publicPath
								+ ". Run scripts/generate-jwt-keys.ps1 or enable payflow.security.jwt.auto-generate-keys for local.");
			}

			Files.createDirectories(privatePath.getParent());
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair pair = generator.generateKeyPair();
			this.privateKey = (RSAPrivateKey) pair.getPrivate();
			this.publicKey = (RSAPublicKey) pair.getPublic();
			writePem(privatePath, "PRIVATE KEY", privateKey.getEncoded());
			writePem(publicPath, "PUBLIC KEY", publicKey.getEncoded());
			log.warn("Generated JWT RSA key pair under {} (gitignored local secrets only)", privatePath.getParent());
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to initialize JWT RSA keys", e);
		}
	}

	public RSAPrivateKey getPrivateKey() {
		return privateKey;
	}

	public RSAPublicKey getPublicKey() {
		return publicKey;
	}

	private static RSAPrivateKey readPrivateKey(Path path) throws Exception {
		byte[] der = decodePem(Files.readString(path, StandardCharsets.UTF_8));
		return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
	}

	private static RSAPublicKey readPublicKey(Path path) throws Exception {
		byte[] der = decodePem(Files.readString(path, StandardCharsets.UTF_8));
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
	}

	private static byte[] decodePem(String pem) {
		String normalized = pem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(normalized);
	}

	private static void writePem(Path path, String type, byte[] der) throws IOException {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
		String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
		Files.writeString(path, pem, StandardCharsets.UTF_8);
	}
}
