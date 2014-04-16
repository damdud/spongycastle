package org.bouncycastle.crypto.tls.test;

import java.security.SecureRandom;

import junit.framework.TestCase;

import org.bouncycastle.crypto.tls.DTLSClientProtocol;
import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DTLSTransport;
import org.bouncycastle.crypto.tls.DatagramTransport;
import org.bouncycastle.crypto.tls.ProtocolVersion;
import org.bouncycastle.util.Arrays;

public class DTLSTestCase extends TestCase
{
    private static void checkDTLSVersion(ProtocolVersion version)
    {
        if (version != null && !version.isDTLS())
        {
            throw new IllegalStateException("Non-DTLS version");
        }
    }

    protected final TlsTestConfig config;

    public DTLSTestCase(TlsTestConfig config, String name)
    {
        checkDTLSVersion(config.clientMinimumVersion);
        checkDTLSVersion(config.clientOfferVersion);
        checkDTLSVersion(config.serverMaximumVersion);
        checkDTLSVersion(config.serverMinimumVersion);

        this.config = config;

        setName(name);
    }

    protected void runTest() throws Throwable
    {
        SecureRandom secureRandom = new SecureRandom();

        DTLSClientProtocol clientProtocol = new DTLSClientProtocol(secureRandom);
        DTLSServerProtocol serverProtocol = new DTLSServerProtocol(secureRandom);

        MockDatagramAssociation network = new MockDatagramAssociation(1500);

        TlsTestClientImpl clientImpl = new TlsTestClientImpl(config);
        TlsTestServerImpl serverImpl = new TlsTestServerImpl(config);

        ServerThread serverThread = new ServerThread(serverProtocol, network.getServer(), serverImpl);
        serverThread.start();

        Exception caught = null;
        try
        {
            DatagramTransport clientTransport = network.getClient();
    
            if (TlsTestConfig.DEBUG)
            {
                clientTransport = new LoggingDatagramTransport(clientTransport, System.out);
            }
    
            DTLSTransport dtlsClient = clientProtocol.connect(clientImpl, clientTransport);
    
            for (int i = 1; i <= 10; ++i)
            {
                byte[] data = new byte[i];
                Arrays.fill(data, (byte)i);
                dtlsClient.send(data, 0, data.length);
            }
    
            byte[] buf = new byte[dtlsClient.getReceiveLimit()];
            while (dtlsClient.receive(buf, 0, buf.length, 100) >= 0)
            {
            }
    
            dtlsClient.close();
        }
        catch (Exception e)
        {
            caught = e;
            logException(caught);
        }

        serverThread.shutdown();

        // TODO Add checks that the various streams were closed

        assertEquals("Client fatal alert connection end", config.expectFatalAlertConnectionEnd, clientImpl.firstFatalAlertConnectionEnd);
        assertEquals("Server fatal alert connection end", config.expectFatalAlertConnectionEnd, serverImpl.firstFatalAlertConnectionEnd);

        assertEquals("Client fatal alert description", config.expectFatalAlertDescription, clientImpl.firstFatalAlertDescription);
        assertEquals("Server fatal alert description", config.expectFatalAlertDescription, serverImpl.firstFatalAlertDescription);

        if (config.expectFatalAlertConnectionEnd == -1)
        {
            assertNull("Unexpected client exception", caught);
            assertNull("Unexpected server exception", serverThread.caught);
        }
    }

    protected  void logException(Exception e)
    {
        if (TlsTestConfig.DEBUG)
        {
            e.printStackTrace();
        }
    }

    class ServerThread
        extends Thread
    {
        private final DTLSServerProtocol serverProtocol;
        private final DatagramTransport serverTransport;
        private final TlsTestServerImpl serverImpl;

        private volatile boolean isShutdown = false;
        Exception caught = null;

        ServerThread(DTLSServerProtocol serverProtocol, DatagramTransport serverTransport, TlsTestServerImpl serverImpl)
        {
            this.serverProtocol = serverProtocol;
            this.serverTransport = serverTransport;
            this.serverImpl = serverImpl;
        }

        public void run()
        {
            try
            {
                DTLSTransport dtlsServer = serverProtocol.accept(serverImpl, serverTransport);
                byte[] buf = new byte[dtlsServer.getReceiveLimit()];
                while (!isShutdown)
                {
                    int length = dtlsServer.receive(buf, 0, buf.length, 100);
                    if (length >= 0)
                    {
                        dtlsServer.send(buf, 0, length);
                    }
                }
                dtlsServer.close();
            }
            catch (Exception e)
            {
                caught = e;
                logException(caught);
            }
        }

        void shutdown()
            throws InterruptedException
        {
            if (!isShutdown)
            {
                isShutdown = true;
                this.interrupt();
                this.join();
            }
        }
    }
}
