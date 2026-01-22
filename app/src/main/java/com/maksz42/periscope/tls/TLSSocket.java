package com.maksz42.periscope.tls;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

class TLSSocket extends SSLSocket {
  private final SSLSocket delegate;
  private String[] spoofedEnabledProtocols;

  static Socket createTLSSocket(Socket socket) {
    if (socket instanceof SSLSocket sslSocket) {
      return new TLSSocket(sslSocket);
    }
    return socket;
  }

  private TLSSocket(SSLSocket sslSocket) {
    this.delegate = sslSocket;
    this.spoofedEnabledProtocols = sslSocket.getEnabledProtocols();
    sslSocket.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.3" });
  }


  // --------------------------------------------
  // spoof enabled protocols
  // vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv

  @Override
  public void setEnabledProtocols(String[] protocols) {
    spoofedEnabledProtocols = protocols.clone();
  }

  @Override
  public String[] getEnabledProtocols() {
    return spoofedEnabledProtocols.clone();
  }



  // --------------------------------------------
  // just delegate everything else
  // vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv


  @Override
  public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
    delegate.addHandshakeCompletedListener(listener);
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  @Override
  public String getApplicationProtocol() {
    return delegate.getApplicationProtocol();
  }

  @Override
  public boolean getEnableSessionCreation() {
    return delegate.getEnableSessionCreation();
  }

  @Override
  public String[] getEnabledCipherSuites() {
    return delegate.getEnabledCipherSuites();
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  @Override
  public String getHandshakeApplicationProtocol() {
    return delegate.getHandshakeApplicationProtocol();
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  @Override
  public BiFunction<SSLSocket, List<String>, String> getHandshakeApplicationProtocolSelector() {
    return delegate.getHandshakeApplicationProtocolSelector();
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  public SSLSession getHandshakeSession() {
    return delegate.getHandshakeSession();
  }

  @Override
  public boolean getNeedClientAuth() {
    return delegate.getNeedClientAuth();
  }

  @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
  @Override
  public SSLParameters getSSLParameters() {
    return delegate.getSSLParameters();
  }

  @Override
  public SSLSession getSession() {
    return delegate.getSession();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public String[] getSupportedProtocols() {
    return delegate.getSupportedProtocols();
  }

  @Override
  public boolean getUseClientMode() {
    return delegate.getUseClientMode();
  }

  @Override
  public boolean getWantClientAuth() {
    return delegate.getWantClientAuth();
  }

  @Override
  public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
    delegate.removeHandshakeCompletedListener(listener);
  }

  @Override
  public void setEnableSessionCreation(boolean flag) {
    delegate.setEnableSessionCreation(flag);
  }

  @Override
  public void setEnabledCipherSuites(String[] suites) {
    delegate.setEnabledCipherSuites(suites);
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  @Override
  public void setHandshakeApplicationProtocolSelector(BiFunction<SSLSocket, List<String>, String> selector) {
    delegate.setHandshakeApplicationProtocolSelector(selector);
  }

  @Override
  public void setNeedClientAuth(boolean need) {
    delegate.setNeedClientAuth(need);
  }

  @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
  @Override
  public void setSSLParameters(SSLParameters params) {
    delegate.setSSLParameters(params);
  }

  @Override
  public void setUseClientMode(boolean mode) {
    delegate.setUseClientMode(mode);
  }

  @Override
  public void setWantClientAuth(boolean want) {
    delegate.setWantClientAuth(want);
  }

  @Override
  public void startHandshake() throws IOException {
    delegate.startHandshake();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    delegate.bind(bindpoint);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    delegate.connect(endpoint);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    delegate.connect(endpoint, timeout);
  }

  @Override
  public SocketChannel getChannel() {
    return delegate.getChannel();
  }

  @Override
  public InetAddress getInetAddress() {
    return delegate.getInetAddress();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return delegate.getInputStream();
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return delegate.getKeepAlive();
  }

  @Override
  public InetAddress getLocalAddress() {
    return delegate.getLocalAddress();
  }

  @Override
  public int getLocalPort() {
    return delegate.getLocalPort();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return delegate.getLocalSocketAddress();
  }

  @Override
  public boolean getOOBInline() throws SocketException {
    return delegate.getOOBInline();
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    return delegate.getOption(name);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return delegate.getOutputStream();
  }

  @Override
  public int getPort() {
    return delegate.getPort();
  }

  @Override
  public int getReceiveBufferSize() throws SocketException {
    return delegate.getReceiveBufferSize();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return delegate.getRemoteSocketAddress();
  }

  @Override
  public boolean getReuseAddress() throws SocketException {
    return delegate.getReuseAddress();
  }

  @Override
  public int getSendBufferSize() throws SocketException {
    return delegate.getSendBufferSize();
  }

  @Override
  public int getSoLinger() throws SocketException {
    return delegate.getSoLinger();
  }

  @Override
  public int getSoTimeout() throws SocketException {
    return delegate.getSoTimeout();
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return delegate.getTcpNoDelay();
  }

  @Override
  public int getTrafficClass() throws SocketException {
    return delegate.getTrafficClass();
  }

  @Override
  public boolean isBound() {
    return delegate.isBound();
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public boolean isConnected() {
    return delegate.isConnected();
  }

  @Override
  public boolean isInputShutdown() {
    return delegate.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return delegate.isOutputShutdown();
  }

  @Override
  public void sendUrgentData(int data) throws IOException {
    delegate.sendUrgentData(data);
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    delegate.setKeepAlive(on);
  }

  @Override
  public void setOOBInline(boolean on) throws SocketException {
    delegate.setOOBInline(on);
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @Override
  public <T> Socket setOption(SocketOption<T> name, T value) throws IOException {
    return delegate.setOption(name, value);
  }

  @Override
  public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
  }

  @Override
  public void setReceiveBufferSize(int size) throws SocketException {
    delegate.setReceiveBufferSize(size);
  }

  @Override
  public void setReuseAddress(boolean on) throws SocketException {
    delegate.setReuseAddress(on);
  }

  @Override
  public void setSendBufferSize(int size) throws SocketException {
    delegate.setSendBufferSize(size);
  }

  @Override
  public void setSoLinger(boolean on, int linger) throws SocketException {
    delegate.setSoLinger(on, linger);
  }

  @Override
  public void setSoTimeout(int timeout) throws SocketException {
    delegate.setSoTimeout(timeout);
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    delegate.setTcpNoDelay(on);
  }

  @Override
  public void setTrafficClass(int tc) throws SocketException {
    delegate.setTrafficClass(tc);
  }

  @Override
  public void shutdownInput() throws IOException {
    delegate.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    delegate.shutdownOutput();
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return delegate.supportedOptions();
  }

}
