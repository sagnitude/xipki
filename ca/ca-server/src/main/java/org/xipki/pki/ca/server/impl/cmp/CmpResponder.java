/*
 *
 * Copyright (c) 2013 - 2016 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ca.server.impl.cmp;

import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Date;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.ErrorMsgContent;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIHeaderBuilder;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.cmp.CMPException;
import org.bouncycastle.cert.cmp.GeneralPKIMessage;
import org.bouncycastle.cert.cmp.ProtectedPKIMessage;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.api.AuditEvent;
import org.xipki.commons.audit.api.AuditLevel;
import org.xipki.commons.audit.api.AuditStatus;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.ConcurrentContentSigner;
import org.xipki.commons.security.SecurityFactory;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.RequestorInfo;
import org.xipki.pki.ca.common.cmp.CmpUtf8Pairs;
import org.xipki.pki.ca.common.cmp.CmpUtil;
import org.xipki.pki.ca.common.cmp.ProtectionResult;
import org.xipki.pki.ca.common.cmp.ProtectionVerificationResult;
import org.xipki.pki.ca.server.mgmt.api.CmpControl;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

abstract class CmpResponder {

    private static final Logger LOG = LoggerFactory.getLogger(CmpResponder.class);

    protected final SecurityFactory securityFactory;

    private final SecureRandom random = new SecureRandom();

    protected CmpResponder(final SecurityFactory securityFactory) {
        this.securityFactory = ParamUtil.requireNonNull("securityFactory", securityFactory);
    }

    protected abstract ConcurrentContentSigner getSigner();

    protected abstract GeneralName getSender();

    protected abstract boolean intendsMe(GeneralName requestRecipient);

    public boolean isInService() {
        try {
            return getSigner() != null;
        } catch (Exception ex) {
            LogUtil.error(LOG, ex, "could not get responder signer");
            return false;
        }
    }

    /**
     * @return never returns {@code null}.
     */
    protected abstract CmpControl getCmpControl();

    protected abstract CmpRequestorInfo getRequestor(X500Name requestorSender);

    protected abstract CmpRequestorInfo getRequestor(X509Certificate requestorCert);

    private CmpRequestorInfo getRequestor(final PKIHeader reqHeader) {
        GeneralName requestSender = reqHeader.getSender();
        if (requestSender.getTagNo() != GeneralName.directoryName) {
            return null;
        }

        return getRequestor((X500Name) requestSender.getName());
    } // method getRequestor

    protected abstract PKIMessage doProcessPkiMessage(@Nullable PKIMessage request,
            @Nonnull RequestorInfo requestor, @Nullable String user,
            @Nonnull ASN1OctetString transactionId, @Nonnull GeneralPKIMessage pkiMessage,
            @Nonnull AuditEvent auditEvent);

    public PKIMessage processPkiMessage(final PKIMessage pkiMessage,
            final X509Certificate tlsClientCert, final AuditEvent auditEvent) {
        ParamUtil.requireNonNull("pkiMessage", pkiMessage);
        ParamUtil.requireNonNull("auditEvent", auditEvent);
        GeneralPKIMessage message = new GeneralPKIMessage(pkiMessage);

        PKIHeader reqHeader = message.getHeader();
        ASN1OctetString tid = reqHeader.getTransactionID();

        if (tid == null) {
            byte[] randomBytes = randomTransactionId();
            tid = new DEROctetString(randomBytes);
        }
        String tidStr = Hex.toHexString(tid.getOctets());
        auditEvent.addEventData("tid", tidStr);

        CmpControl cmpControl = getCmpControl();

        Integer failureCode = null;
        String statusText = null;

        Date messageTime = null;
        if (reqHeader.getMessageTime() != null) {
            try {
                messageTime = reqHeader.getMessageTime().getDate();
            } catch (ParseException ex) {
                LogUtil.error(LOG, ex, "tid=" + tidStr + ": could not parse messageDate");
                messageTime = null;
            }
        }

        GeneralName recipient = reqHeader.getRecipient();
        boolean intentMe = (recipient == null) ? true : intendsMe(recipient);
        if (!intentMe) {
            LOG.warn("tid={}: I am not the intented recipient, but '{}'", tid,
                    reqHeader.getRecipient());
            failureCode = PKIFailureInfo.badRequest;
            statusText = "I am not the intended recipient";
        } else if (messageTime == null) {
            if (cmpControl.isMessageTimeRequired()) {
                failureCode = PKIFailureInfo.missingTimeStamp;
                statusText = "missing timestamp";
            }
        } else {
            long messageTimeBias = cmpControl.getMessageTimeBias();
            if (messageTimeBias < 0) {
                messageTimeBias *= -1;
            }

            long msgTimeMs = messageTime.getTime();
            long currentTimeMs = System.currentTimeMillis();
            long bias = (msgTimeMs - currentTimeMs) / 1000L;
            if (bias > messageTimeBias) {
                failureCode = PKIFailureInfo.badTime;
                statusText = "message time is in the future";
            } else if (bias * -1 > messageTimeBias) {
                failureCode = PKIFailureInfo.badTime;
                statusText = "message too old";
            }
        }

        if (failureCode != null) {
            auditEvent.setLevel(AuditLevel.INFO);
            auditEvent.setStatus(AuditStatus.FAILED);
            auditEvent.addEventData("message", statusText);
            return buildErrorPkiMessage(tid, reqHeader, failureCode, statusText);
        }

        boolean isProtected = message.hasProtection();
        CmpRequestorInfo requestor = null;

        String errorStatus;

        if (isProtected) {
            try {
                ProtectionVerificationResult verificationResult = verifyProtection(tidStr,
                        message, cmpControl);
                ProtectionResult pr = verificationResult.getProtectionResult();
                switch (pr) {
                case VALID:
                    errorStatus = null;
                    break;
                case INVALID:
                    errorStatus = "request is protected by signature but invalid";
                    break;
                case NOT_SIGNATURE_BASED:
                    errorStatus = "request is not protected by signature";
                    break;
                case SENDER_NOT_AUTHORIZED:
                    errorStatus =
                        "request is protected by signature but the requestor is not authorized";
                    break;
                case SIGALGO_FORBIDDEN:
                    errorStatus = "request is protected by signature but the protection algorithm"
                        + " is forbidden";
                    break;
                default:
                    throw new RuntimeException(
                        "should not reach here, unknown ProtectionResult " + pr);
                } // end switch
                requestor = (CmpRequestorInfo) verificationResult.getRequestor();
            } catch (Exception ex) {
                LogUtil.error(LOG, ex, "tid=" + tidStr + ": could not verify the signature");
                errorStatus = "request has invalid signature based protection";
            }
        } else if (tlsClientCert != null) {
            boolean authorized = false;

            requestor = getRequestor(reqHeader);
            if (requestor != null) {
                if (tlsClientCert.equals(requestor.getCert().getCert())) {
                    authorized = true;
                }
            }

            if (authorized) {
                errorStatus = null;
            } else {
                LOG.warn("tid={}: not authorized requestor (TLS client '{}')", tid,
                        X509Util.getRfc4519Name(tlsClientCert.getSubjectX500Principal()));
                errorStatus = "requestor (TLS client certificate) is not authorized";
            }
        } else {
            errorStatus = "request has no protection";
            requestor = null;
        }

        CmpUtf8Pairs keyvalues = CmpUtil.extract(reqHeader.getGeneralInfo());
        String username = (keyvalues == null) ? null : keyvalues.getValue(CmpUtf8Pairs.KEY_USER);
        if (username != null) {
            if (username.indexOf('*') != -1 || username.indexOf('%') != -1) {
                errorStatus = "user could not contains characters '*' and '%'";
            }
        }

        if (errorStatus != null) {
            auditEvent.setLevel(AuditLevel.INFO);
            auditEvent.setStatus(AuditStatus.FAILED);
            auditEvent.addEventData("message", errorStatus);
            return buildErrorPkiMessage(tid, reqHeader, PKIFailureInfo.badMessageCheck,
                    errorStatus);
        }

        PKIMessage resp = doProcessPkiMessage(pkiMessage, requestor, username, tid, message,
                auditEvent);

        if (isProtected) {
            resp = addProtection(resp, auditEvent);
        } else {
            // protected by TLS connection
        }

        return resp;
    } // method processPkiMessage

    protected byte[] randomTransactionId() {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        return bytes;
    }

    private ProtectionVerificationResult verifyProtection(final String tid,
            final GeneralPKIMessage pkiMessage, final CmpControl cmpControl)
    throws CMPException, InvalidKeyException, OperatorCreationException {
        ProtectedPKIMessage protectedMsg = new ProtectedPKIMessage(pkiMessage);

        if (protectedMsg.hasPasswordBasedMacProtection()) {
            LOG.warn("NOT_SIGNAUTRE_BASED: {}",
                    pkiMessage.getHeader().getProtectionAlg().getAlgorithm().getId());
            return new ProtectionVerificationResult(null, ProtectionResult.NOT_SIGNATURE_BASED);
        }

        PKIHeader header = protectedMsg.getHeader();
        AlgorithmIdentifier protectionAlg = header.getProtectionAlg();
        if (!cmpControl.getSigAlgoValidator().isAlgorithmPermitted(protectionAlg)) {
            LOG.warn("SIG_ALGO_FORBIDDEN: {}",
                    pkiMessage.getHeader().getProtectionAlg().getAlgorithm().getId());
            return new ProtectionVerificationResult(null, ProtectionResult.SIGALGO_FORBIDDEN);
        }

        CmpRequestorInfo requestor = getRequestor(header);
        if (requestor == null) {
            LOG.warn("tid={}: not authorized requestor '{}'", tid, header.getSender());
            return new ProtectionVerificationResult(null, ProtectionResult.SENDER_NOT_AUTHORIZED);
        }

        ContentVerifierProvider verifierProvider = securityFactory.getContentVerifierProvider(
                requestor.getCert().getCert());
        if (verifierProvider == null) {
            LOG.warn("tid={}: not authorized requestor '{}'", tid, header.getSender());
            return new ProtectionVerificationResult(requestor,
                    ProtectionResult.SENDER_NOT_AUTHORIZED);
        }

        boolean signatureValid = protectedMsg.verify(verifierProvider);
        return new ProtectionVerificationResult(requestor,
                signatureValid ? ProtectionResult.VALID : ProtectionResult.INVALID);
    } // method verifyProtection

    private PKIMessage addProtection(final PKIMessage pkiMessage, final AuditEvent auditEvent) {
        try {
            return CmpUtil.addProtection(pkiMessage, getSigner(), getSender(),
                    getCmpControl().isSendResponderCert());
        } catch (Exception ex) {
            LogUtil.error(LOG, ex, "could not add protection to the PKI message");
            PKIStatusInfo status = generateCmpRejectionStatus(
                    PKIFailureInfo.systemFailure, "could not sign the PKIMessage");

            auditEvent.setLevel(AuditLevel.ERROR);
            auditEvent.setStatus(AuditStatus.FAILED);
            auditEvent.addEventData("message", "could not sign the PKIMessage");
            PKIBody body = new PKIBody(PKIBody.TYPE_ERROR, new ErrorMsgContent(status));
            return new PKIMessage(pkiMessage.getHeader(), body);
        }
    } // method addProtection

    protected PKIMessage buildErrorPkiMessage(final ASN1OctetString tid,
            final PKIHeader requestHeader, final int failureCode, final String statusText) {
        GeneralName respRecipient = requestHeader.getSender();

        PKIHeaderBuilder respHeader = new PKIHeaderBuilder(
                requestHeader.getPvno().getValue().intValue(), getSender(), respRecipient);
        respHeader.setMessageTime(new ASN1GeneralizedTime(new Date()));
        if (tid != null) {
            respHeader.setTransactionID(tid);
        }

        PKIStatusInfo status = generateCmpRejectionStatus(failureCode, statusText);
        ErrorMsgContent error = new ErrorMsgContent(status);
        PKIBody body = new PKIBody(PKIBody.TYPE_ERROR, error);

        return new PKIMessage(respHeader.build(), body);
    } // method buildErrorPkiMessage

    protected PKIStatusInfo generateCmpRejectionStatus(final Integer info,
            final String errorMessage) {
        return generateCmpRejectionStatus(PKIStatus.rejection, info, errorMessage);
    } // method generateCmpRejectionStatus

    protected PKIStatusInfo generateCmpRejectionStatus(final PKIStatus status, final Integer info,
            final String errorMessage) {
        PKIFreeText statusMessage = (errorMessage == null) ? null : new PKIFreeText(errorMessage);
        PKIFailureInfo failureInfo = (info == null) ? null : new PKIFailureInfo(info);
        return new PKIStatusInfo(status, statusMessage, failureInfo);
    } // method generateCmpRejectionStatus

    public X500Name getResponderSubject() {
        GeneralName sender = getSender();
        return (sender == null) ? null : (X500Name) sender.getName();
    }

    public X509Certificate getResponderCert() {
        ConcurrentContentSigner signer = getSigner();
        return (signer == null) ? null : signer.getCertificate();
    }

}
