/*
 * Copyright (c) 2020 Red Hat, Inc. and others
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.JksOptions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Util class to generate SSL key pairs and certificates for test purpose.
 *
 * All generated key pairs and certificates are in memory.
 *
 * @author <a href="mailto: aoingl@gmail.com">Lin Gao</a>
 */
public class SSLKeyPairCerts {

  private static final String SERVER_CERT_SUBJECT = "CN=Vertx Server, OU=Middleware Runtime, O=Red Hat, C=US";
  private static final String CLIENT_CERT_SUBJECT = "CN=Vertx Client, OU=Middleware Runtime, O=Red Hat, C=US";
  private static final String PASSWORD = "wibble";

  private JksOptions serverKeyStore;
  private JksOptions serverTrustStore;
  private JksOptions clientKeyStore;
  private JksOptions clientTrustStore;

  public SSLKeyPairCerts() {
  }

  /**
   * Creates 2 way SSL key pairs and certificates.
   *
   *<p>
   * This will initialize 4 KeyStores in <code>JKS</code> type:
   * <ul>
   *     <li>server's keystore</li>
   *     <li>server's truststore with client's certificate imported</li>
   *     <li>client's keystore</li>
   *     <li>client's truststore with server's certificate imported</li>
   * </ul>
   *</p>
   * @return self
   */
  public SSLKeyPairCerts createTwoWaySSL() {
    try {
      KeyPair serverRSAKeyPair = generateRSAKeyPair(2048);
      Certificate serverCert = generateSelfSignedCert(SERVER_CERT_SUBJECT, serverRSAKeyPair);

      KeyPair clientRSAKeyPair = generateRSAKeyPair(2048);
      Certificate clientCert = generateSelfSignedCert(CLIENT_CERT_SUBJECT, clientRSAKeyPair);

      KeyStore serverKeyStore = emptyJKSStore(PASSWORD);
      serverKeyStore.setKeyEntry("localserver", serverRSAKeyPair.getPrivate(), PASSWORD.toCharArray(), new Certificate[]{serverCert});

      KeyStore serverTrustStore = emptyJKSStore(PASSWORD);
      serverTrustStore.setCertificateEntry("clientcert", clientCert);

      KeyStore clientKeyStore = emptyJKSStore(PASSWORD);
      clientKeyStore.setKeyEntry("localclient", clientRSAKeyPair.getPrivate(), PASSWORD.toCharArray(), new Certificate[]{clientCert});

      KeyStore clientTrustStore = emptyJKSStore(PASSWORD);
      clientTrustStore.setCertificateEntry("servercert", serverCert);

      ByteArrayOutputStream serverKeyStoreOutputStream = new ByteArrayOutputStream(512);
      serverKeyStore.store(serverKeyStoreOutputStream, PASSWORD.toCharArray());
      this.serverKeyStore = new JksOptions().setPassword(PASSWORD).setValue(Buffer.buffer(serverKeyStoreOutputStream.toByteArray()));

      ByteArrayOutputStream serverTrustStoreOutputStream = new ByteArrayOutputStream(512);
      serverTrustStore.store(serverTrustStoreOutputStream, PASSWORD.toCharArray());
      this.serverTrustStore = new JksOptions().setPassword(PASSWORD).setValue(Buffer.buffer(serverTrustStoreOutputStream.toByteArray()));

      ByteArrayOutputStream clientKeyStoreOutputStream = new ByteArrayOutputStream(512);
      clientKeyStore.store(clientKeyStoreOutputStream, PASSWORD.toCharArray());
      this.clientKeyStore = new JksOptions().setPassword(PASSWORD).setValue(Buffer.buffer(clientKeyStoreOutputStream.toByteArray()));

      ByteArrayOutputStream clientTrustStoreOutputStream = new ByteArrayOutputStream(512);
      clientTrustStore.store(clientTrustStoreOutputStream, PASSWORD.toCharArray());
      this.clientTrustStore = new JksOptions().setPassword(PASSWORD).setValue(Buffer.buffer(clientTrustStoreOutputStream.toByteArray()));
    } catch (Exception e) {
      throw new RuntimeException("Cannot generate SSL key pairs and certificates", e);
    }
    return this;
  }

  // refer to: https://github.com/vert-x3/vertx-config/blob/4.0.0-milestone4/vertx-config-vault/src/test/java/io/vertx/config/vault/utils/Certificates.java#L149
  private X509Certificate generateSelfSignedCert(String certSub, KeyPair keyPair) throws Exception {
    final X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
      new org.bouncycastle.asn1.x500.X500Name(certSub),
      BigInteger.ONE,
      new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30),
      new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)),
      new X500Name(certSub),
      SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
    );
    final GeneralNames subjectAltNames = new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
    certificateBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName, false, subjectAltNames);

    final AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1WithRSAEncryption");
    final AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
    final BcContentSignerBuilder signerBuilder = new BcRSAContentSignerBuilder(sigAlgId, digAlgId);
    final AsymmetricKeyParameter keyp = PrivateKeyFactory.createKey(keyPair.getPrivate().getEncoded());
    final ContentSigner signer = signerBuilder.build(keyp);
    final X509CertificateHolder x509CertificateHolder = certificateBuilder.build(signer);
    final X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(x509CertificateHolder);
    certificate.checkValidity(new Date());
    certificate.verify(keyPair.getPublic());
    return certificate;
  }

  private KeyPair generateRSAKeyPair(int keySize) throws NoSuchAlgorithmException {
    final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(keySize);
    return keyPairGenerator.genKeyPair();
  }

  private KeyStore emptyJKSStore(String password) throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, password.toCharArray());
    return ks;
  }

  /**
   * @return the server's keystore options
   */
  public JksOptions getServerKeyStore() {
    return serverKeyStore;
  }

  /**
   * @return the server's truststore options
   */
  public JksOptions getServerTrustStore() {
    return serverTrustStore;
  }

  /**
   * @return the client's keystore options
   */
  public JksOptions getClientKeyStore() {
    return clientKeyStore;
  }

  /**
   * @return the client's truststore options
   */
  public JksOptions getClientTrustStore() {
    return clientTrustStore;
  }
}
