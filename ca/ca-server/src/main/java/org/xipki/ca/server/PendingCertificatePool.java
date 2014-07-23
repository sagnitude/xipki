/*
 * Copyright (c) 2014 Lijun Liao
 *
 * TO-BE-DEFINE
 *
 */

package org.xipki.ca.server;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.util.encoders.Hex;
import org.xipki.ca.api.publisher.CertificateInfo;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

class PendingCertificatePool
{
    private static class MyEntry
    {
        private final BigInteger certReqId;
        private final long waitForConfirmTill;
        private final CertificateInfo certInfo;

        public MyEntry(BigInteger certReqId,
                long waitForConfirmTill,
                CertificateInfo certInfo)
        {
            super();
            ParamChecker.assertNotNull("certReqId", certReqId);
            ParamChecker.assertNotNull("certInfo", certInfo);

            this.certReqId = certReqId;
            this.waitForConfirmTill = waitForConfirmTill;
            this.certInfo = certInfo;
        }

        @Override
        public boolean equals(Object b)
        {
            if(b instanceof MyEntry == false)
            {
                return false;
            }

            MyEntry another = (MyEntry) b;
            return certReqId.equals(another.certReqId) &&
                    certInfo.equals(another.certInfo);
        }
    }

    private final Map<String, Set<MyEntry>> map = new ConcurrentHashMap<>();

    PendingCertificatePool()
    {
    }

    synchronized void addCertificate(
            byte[] tid, BigInteger certReqId, CertificateInfo certInfo, long waitForConfirmTill)
    {
        if(certInfo.isAlreadyIssued())
        {
            return;
        }

        String hexTid = Hex.toHexString(tid);
        Set<MyEntry> entries = map.get(hexTid);
        if(entries == null)
        {
            entries = new HashSet<>();
            map.put(hexTid, entries);
        }

        MyEntry myEntry = new MyEntry(certReqId, waitForConfirmTill, certInfo);
        entries.add(myEntry);
    }

    synchronized CertificateInfo removeCertificate(
            byte[] transactionId, BigInteger certReqId, byte[] certHash)
    {
        String hexTid = Hex.toHexString(transactionId);
        Set<MyEntry> entries = map.get(hexTid);
        if(entries == null)
        {
            return null;
        }

        MyEntry retEntry = null;
        for(MyEntry entry : entries)
        {
            if(certReqId.equals(entry.certReqId))
            {
                retEntry = entry;
                break;
            }
        }

        if(retEntry != null)
        {
            entries.remove(retEntry);
        }

        if(entries.isEmpty())
        {
            map.remove(hexTid);
        }

        return retEntry.certInfo;
    }

    synchronized Set<CertificateInfo> removeCertificates(byte[] transactionId)
    {
        Set<MyEntry> entries = map.remove(Hex.toHexString(transactionId));
        if(entries == null)
        {
            return null;
        }

        Set<CertificateInfo> ret = new HashSet<>();
        for(MyEntry myEntry :entries)
        {
            ret.add(myEntry.certInfo);
        }
        return ret;
    }

    synchronized Set<CertificateInfo> removeConfirmTimeoutedCertificates()
    {
        if(map.isEmpty())
        {
            return null;
        }

        long now = System.currentTimeMillis();

        Set<CertificateInfo> ret = new HashSet<>();

        for(String tid : map.keySet())
        {
            Set<MyEntry> entries = map.get(tid);
            for(MyEntry entry : entries)
            {
                if(entry.waitForConfirmTill < now)
                {
                    ret.add(entry.certInfo);
                }
            }
        }
        return ret;
    }

}
