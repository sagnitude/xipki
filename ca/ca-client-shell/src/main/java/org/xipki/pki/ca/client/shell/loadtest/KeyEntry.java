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

package org.xipki.pki.ca.client.shell.loadtest;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Base64;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.util.AlgorithmUtil;
import org.xipki.commons.security.util.KeyUtil;
import org.xipki.commons.security.util.SignerUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class KeyEntry {

    // CHECKSTYLE:SKIP
    public static final class RSAKeyEntry extends KeyEntry {

        private final BigInteger baseN;

        public RSAKeyEntry(final int keysize) throws Exception {
            if (keysize % 1024 != 0) {
                throw new IllegalArgumentException("invalid RSA keysize " + keysize);
            }

            BigInteger modulus = BigInteger.valueOf(0);
            modulus = modulus.setBit(keysize - 1);
            for (int i = 32; i < keysize - 1; i += 2) {
                modulus = modulus.setBit(i);
            }
            this.baseN = modulus;
        }

        @Override
        public SubjectPublicKeyInfo getSubjectPublicKeyInfo(final long index) {
            BigInteger modulus = baseN.add(BigInteger.valueOf(index));

            try {
                RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, BigInteger.valueOf(65537));
                return SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                        SignerUtil.generateRSAPublicKeyParameter(
                                KeyUtil.generateRSAPublicKey(keySpec)));
            } catch (InvalidKeySpecException ex) {
                throw new RuntimeException(
                        "InvalidKeySpecException while constructing SubjectPublicKeyInfo", ex);
            } catch (IOException ex) {
                throw new RuntimeException(
                        "IOException while constructing SubjectPublicKeyInfo", ex);
            }
        }

    } // class RSAKeyEntry

    // CHECKSTYLE:SKIP
    public static final class DSAKeyEntry extends KeyEntry {

        private static final String P_1024 =
                "AM/6AjYLnnzRa99zmdhuMmikFKF/HhotHagBxoHlT4alq415sX94psaJPI3D5+/e"
                + "YUaZbnFMn/IEBh6YyEL4zPQs4xOYdcNoMJ6XccGelEHjVWDvocf00L417XiTJObM"
                + "UVHgFrkTPZ7G/mDBFMQ1WVrSSwnjPPDSCkIRiiGiCQqv";

        private static final String Q_1024 =
                "ANMDutPywwIBAbqpSxN8CAvbPkaL";

        private static final String G_1024 =
                "AcFvVWWRJlZsT2xCtto0gZj6QyaVAVJmvkZinCm2kafbFPQ+MLeZJKk3mvCaq2i8"
                + "DPptJ5LC1FaaE+Is4rhX0BcW5/rxxSk6JVboakH48KEg5nZvzphMNe9mC2bYV29z"
                + "BbyiYvs9v7EhAI7bKlvOaAb6XyB//fUh1+GYzP15HOw=";

        private static final String Y_1024 =
                "AMjz/ko+WVNXbLo7ixe+iIU2OpgngYIaSZQkbFFQ6E/ePlmgPlFY9gOV7CrjKYfn"
                + "MgU33SZHipa7Zo6LmgjNZ6VE2FE8PGE+CHM+XZMQlZdOtKZLMfRWUdYfUZ5D7j+1"
                + "3HlpW2ahfeClF5xuhwjcTDm7VdxZ+rVscp+QzF7Je35p";

        private static final String P_2048 =
                "ANAwGdtm8DJ6YHsC9A9c5bmg8saK4TShCmWoC0sLRwaueN/thcXQKjG4qNQu1BiR"
                + "wIGwlPqbX5F+i/kILuJ4xvcoffMsLZG9WoI8bG5Y+Ld9KWOJ/KDiYjdX5R9flbmq"
                + "gnMBUbscsLMStNBaHFZYe58S4uClVS6v7poi6s3e/B3ryvWPG4zIamFxV72DmSWo"
                + "nArfljTleJ/pVqlWW5WOTqXjk11ab53PSJGWPAyhfgZWhHTwsKt+HgemrA8D55us"
                + "vDVPhYNsnEy4FtJEbIJSsIKO4qGrhDA2mZeH30EXhZx83HWRML0GIP+tDguBN+hI"
                + "w7G9DpwQQL+f4lowf9rde38=";

        private static final String Q_2048 =
                "AO236rjUtJKK0CDLx+koJmwrX4xzffhW/hdqCOb8eOmV";

        private static final String G_2048 =
                "ALsVY3D6QRDnBiwfrbl4Br9wE5D9RZlPt6lUYmOIQFptcD1RWP0ZuajmDQ90FLJ/"
                + "jamGgqeIY+ZyA0UZqDcDtfrIB0sOML7xIqqUyTxV/UiIHnWVf9Xa9JGUWrUoqvVG"
                + "qiDI1G0OcIuBykDAHTUo0J54TKZP0DMILkwO0kqf+SM/mH4Q1qxV3RfRDUqc3v9s"
                + "/LgmshkkBsIapsh5AADA2GAoHnrLlgimgJS4zKoytCYYGtL0NqN4vyGiIQaeoOv4"
                + "JRwZ+gQsy7OA0nkDRWnAFsPOnPxQqsEGmfE8VGzXrHl42jcavPuJ1o3CCeMq30D0"
                + "VbEuwEMCUNBcjHTEpF0jnqs=";

        private static final String Y_2048 =
                "Pmn2UvUGcvhAwl57D86OPUdhiPJr4qaBNy9GAkUca4nkoQthP90dhvqB6b4FvJBr"
                + "jsWoPVQSxW+pX2vfbjscQMm4Kt5zaqeHnV/Rod4l5VeW7sqoRqBR7nzlke+xHkT0"
                + "wM3XkCp2E6BTAhT0qtp05w6onOoWIMv5Ydd04xwNB8lFF+A2uJrt3QlHXHvRW1no"
                + "Sf7bRDWC6JKLMRd+G0H8USMMT7UNUgen1lXbIH0q1GDph0z1MZkbUwewE/dG6KBL"
                + "3Km1hBmuWyUrqHLznKYPwhmcOqWpTGn2PJrmdEJdjhdhwpWT1POhiyP+YxxbFNv/"
                + "Kjkdw4wQAVloP0QZ4wH/IA==";

        private static final String P_3072 =
                "AOcper98q/F5dOpeZMTAxQiD3Sptsr0b19WMMwO+/P0YqjiS1LNqhX2ULuCv/oD1"
                + "TtgfRx5bNhsg4jA+mLtGHZAO2nGrF9fwlyxyv5gcx4D6WfDaLx1ZVGGcux8PlgLz"
                + "dshN10t6Icyl28Ky/G8HOFwu7kmHSw6fujsCqW0CvjiRW+sQ9N2Fuzjivg9axCyL"
                + "MtFLXUcQuIyHhlQWSQyrQkhAG0qLfLWpzM0BdWpL0hDSKgjOTNvYphRquqdzn90g"
                + "bXTwcQQdtuiQWg6WGhAz7feyuATmhESE2i2P5LV9LQGBG6j3zfU3ITmyexb4rFbp"
                + "gRSIWZ0nrs5uQ0lYZgUyr+dnzFnNqYZB7aNw5cFR5TWKcxTx0yp9kMMjjZSiQQc2"
                + "TVm254f6rztbERjU7HXxPFROujufBoTeBj0ayNxVJRIBs6XgSpE7/Gw3OJWJVMYT"
                + "5iA0MZv0Vs+WKdHoCKCZ8mfZbIisxCpFV8PW9/Xz6T59rBJxs51ZdwwyHz/EB7es"
                + "EQ==";

        private static final String Q_3072 =
                "ALQa/gl8UpkX42IsgfnihnQDS+NQ5US6eYGDz34guCib";

        private static final String G_3072 =
                "FyAOku6IrTU1iI6S0gl3soIF3mRbA/WiKpXu4ZCOdf9fHA12XtXPWRc7jaFBZucp"
                + "uxYtmDeUwCZRFX+HWpkptVv6LT8OIDbCFFfJlo6i0d1ulq9ybOLtcWXlqIcp4Z0v"
                + "LbFcxUr79CYp/mwdXzMwD+v7GFTa6feJdQENEfnwOGLUItOm+6OBcft+94xvDCmx"
                + "tfYYkgEaFvRFjVKy+9kDuf6OfFKM0RDX4tvvpPwpXdYghjk6C/9e0jqloW20DhsW"
                + "fBDmhp5cPSkjUDDSOz0JrOvT7MBeqefez6PbgMywZFiY+iBOZiIay2o5tI2uOaGq"
                + "z1K/1xo7tXMlZu0wFuxtwrThFbmbGIXAMCC4hNeW4hFKDRnYZtnRKChhh3gNx3eO"
                + "/58as/UIa8ApnjvG9tLpYtmlfJlgzlmEAuuB08qMg+T9tUjKQZWgEOiT6INFZS0P"
                + "mbSZgzJACYY6dMnywnne97TrgCccXuoTRV3derD91tU6lhfR5B4ZADXVClNrf7Nl";

        private static final String Y_3072 =
                "aiFUID1NF6SllNJf2RDMik1rU2A5VQc4pLw8wfvxG2WXCSTeuWnDB7b3HBtQOwox"
                + "lYaDQnKBEcly61aVbLKP+TD8dngJQNlr42M07u2drT2ADbk3cLWQ8lk27LBWjntX"
                + "oXduWz2onw1FEdh2nJSTkHZGBaQpUt8vwxB3IWoGsaazOKLnm/rZlK9g9Rs0RCXA"
                + "8KViQEUuAU1h/tt2bEFgOECQ6efm3oBeG3TfpstMud7QaGyyFnTgRxrZQpC1Et3D"
                + "e8TVvIy3uUjnOjGDVV8qGVjsI3J9zX3w5TbTDB4+lbMpc9/oMd26veU+8+7GY5Sz"
                + "iEPTcZF38POgm8I9Nxe5gxtiNJyOMQZC20rnffJljVLe8L1LrXPj0MJfVZMLXiyz"
                + "SLFRUj7ZpN4rzgQYkB0ETN40DFFk3a2gC9E2g+EdksAW0h+IAElikX4Q4Ja0kzXu"
                + "wJfVmq5g/Sp4ysk+JTzAl6r4T1dDr9BXpGgRdYdgwE/2RTMfunqTcUYlV752sbvy";

        private AlgorithmIdentifier algId;

        private BigInteger baseY;

        public DSAKeyEntry(final int plength) throws Exception {
            if (plength == 1024) {
                init(P_1024, Q_1024, G_1024, Y_1024);
            } else if (plength == 2048) {
                init(P_2048, Q_2048, G_2048, Y_2048);
            } else if (plength == 3072) {
                init(P_3072, Q_3072, G_3072, Y_3072);
            } else {
                if (plength % 1024 != 0) {
                    throw new IllegalArgumentException("invalid DSA pLength " + plength);
                }

                int qlength;
                if (plength >= 2048) {
                    qlength = 256;
                } else {
                    qlength = 160;
                }

                KeyPair kp = KeyUtil.generateDSAKeypair(plength, qlength, new SecureRandom());
                DSAPublicKey pk = (DSAPublicKey) kp.getPublic();

                init(pk.getParams().getP(), pk.getParams().getQ(), pk.getParams().getG(),
                        pk.getY());
            }
        }

        // CHECKSTYLE:OFF
        private void init(final String p, final String q, final String g, final String y) {
            // CHECKSTYLE:ON
            init(base64ToInt(p), base64ToInt(q), base64ToInt(g), base64ToInt(y));
        }

        private static BigInteger base64ToInt(final String base64Str) {
            return new BigInteger(1, Base64.decode(base64Str));
        }

        // CHECKSTYLE:OFF
        private void init(final BigInteger p, final BigInteger q, final BigInteger g,
                final BigInteger y) {
            // CHECKSTYLE:ON
            ASN1EncodableVector vec = new ASN1EncodableVector();
            vec.add(new ASN1Integer(p));
            vec.add(new ASN1Integer(q));
            vec.add(new ASN1Integer(g));
            ASN1Sequence dssParams = new DERSequence(vec);
            this.algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa, dssParams);
            this.baseY = y;
        }

        @Override
        public SubjectPublicKeyInfo getSubjectPublicKeyInfo(final long index) {
            // CHECKSTYLE:SKIP
            BigInteger y = baseY.add(BigInteger.valueOf(index));

            try {
                return new SubjectPublicKeyInfo(
                        algId,
                        new ASN1Integer(y));
            } catch (IOException ex) {
                throw new RuntimeException("IOException while constructing SubjectPublicKeyInfo",
                        ex);
            }
        }

    } // class DSAKeyEntry

    // CHECKSTYLE:SKIP
    public static final class ECKeyEntry extends KeyEntry {

        private final AlgorithmIdentifier algId;

        private final BigInteger basePublicKey;

        public ECKeyEntry(final String curveNameOrOid) throws Exception {
            ParamUtil.requireNonNull("curveNameOrOid", curveNameOrOid);

            ASN1ObjectIdentifier curveOid = AlgorithmUtil.getCurveOidForCurveNameOrOid(
                    curveNameOrOid);
            if (curveOid == null) {
                throw new IllegalArgumentException("unknown curveNameOrOid '" + curveNameOrOid
                        + "'");
            }

            String curveName = AlgorithmUtil.getCurveName(curveOid);
            if (curveName == null) {
                curveName = curveOid.getId();
            }

            algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, curveOid);

            KeyPairGenerator kpgen = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(curveName);
            kpgen.initialize(spec);
            KeyPair kp = kpgen.generateKeyPair();

            ECPoint baseQ = ((BCECPublicKey) kp.getPublic()).getQ();
            basePublicKey = new BigInteger(baseQ.getEncoded(false));
        }

        @Override
        public SubjectPublicKeyInfo getSubjectPublicKeyInfo(final long index) {
            BigInteger publicKey = basePublicKey.add(BigInteger.valueOf(index));
            return new SubjectPublicKeyInfo(algId, publicKey.toByteArray());
        }

    } // class ECKeyEntry

    public abstract SubjectPublicKeyInfo getSubjectPublicKeyInfo(long index);

}
