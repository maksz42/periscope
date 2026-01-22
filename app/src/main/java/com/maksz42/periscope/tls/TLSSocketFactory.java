package com.maksz42.periscope.tls;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class TLSSocketFactory extends SSLSocketFactory {
  private final SSLSocketFactory delegate;

  public TLSSocketFactory(KeyManager[] km, TrustManager[] tm, SecureRandom random)
      throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(km, tm, random);
    this.delegate = sc.getSocketFactory();
  }

  @Override
  public Socket createSocket() throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket());
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose)
      throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket(s, host, port, autoClose));
  }

  @Override
  public Socket createSocket(InetAddress address, int port) throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket(address, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress clientAddress, int clientPort)
      throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket(host, port, clientAddress, clientPort));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort)
      throws IOException {
    return TLSSocket.createTLSSocket(delegate.createSocket(address, port, clientAddress, clientPort));
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }
}
