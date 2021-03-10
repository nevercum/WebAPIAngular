/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 */

/* Copyright  (c) 2002 Graz University of Technology. All rights reserved.
 *
 * Redistribution and use in  source and binary forms, with or without
 * modification, are permitted  provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in  binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment:
 *
 *    "This product includes software developed by IAIK of Graz University of
 *     Technology."
 *
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Graz University of Technology" and "IAIK of Graz University of
 *    Technology" must not be used to endorse or promote products derived from
 *    this software without prior written permission.
 *
 * 5. Products derived from this software may not be called
 *    "IAIK PKCS Wrapper", nor may "IAIK" appear in their name, without prior
 *    written permission of Graz University of Technology.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY  OF SUCH DAMAGE.
 */

#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "sun_security_pkcs11_wrapper_PKCS11.h"

#ifdef P11_ENABLE_GETNATIVEKEYINFO

#define CK_ATTRIBUTES_TEMPLATE_LENGTH (CK_ULONG)61U

static CK_ATTRIBUTE ckpAttributesTemplate[CK_ATTRIBUTES_TEMPLATE_LENGTH] = {
        {CKA_CLASS, 0, 0},
        {CKA_TOKEN, 0, 0},
        {CKA_PRIVATE, 0, 0},
        {CKA_LABEL, 0, 0},
        {CKA_APPLICATION, 0, 0},
        {CKA_VALUE, 0, 0},
        {CKA_OBJECT_ID, 0, 0},
        {CKA_CERTIFICATE_TYPE, 0, 0},
        {CKA_ISSUER, 0, 0},
        {CKA_SERIAL_NUMBER, 0, 0},
        {CKA_AC_ISSUER, 0, 0},
        {CKA_OWNER, 0, 0},
        {CKA_ATTR_TYPES, 0, 0},
        {CKA_TRUSTED, 0, 0},
        {CKA_KEY_TYPE, 0, 0},
        {CKA_SUBJECT, 0, 0},
        {CKA_ID, 0, 0},
        {CKA_SENSITIVE, 0, 0},
        {CKA_ENCRYPT, 0, 0},
        {CKA_DECRYPT, 0, 0},
        {CKA_WRAP, 0, 0},
        {CKA_UNWRAP, 0, 0},
        {CKA_SIGN, 0, 0},
        {CKA_SIGN_RECOVER, 0, 0},
        {CKA_VERIFY, 0, 0},
        {CKA_VERIFY_RECOVER, 0, 0},
        {CKA_DERIVE, 0, 0},
        {CKA_START_DATE, 0, 0},
        {CKA_END_DATE, 0, 0},
        {CKA_MODULUS, 0, 0},
        {CKA_MODULUS_BITS, 0, 0},
        {CKA_PUBLIC_EXPONENT, 0, 0},
        {CKA_PRIVATE_EXPONENT, 0, 0},
        {CKA_PRIME_1, 0, 0},
        {CKA_PRIME_2, 0, 0},
        {CKA_EXPONENT_1, 0, 0},
        {CKA_EXPONENT_2, 0, 0},
        {CKA_COEFFICIENT, 0, 0},
        {CKA_PRIME, 0, 0},
        {CKA_SUBPRIME, 0, 0},
        {CKA_BASE, 0, 0},
        {CKA_PRIME_BITS, 0, 0},
        {CKA_SUB_PRIME_BITS, 0, 0},
        {CKA_VALUE_BITS, 0, 0},
        {CKA_VALUE_LEN, 0, 0},
        {CKA_EXTRACTABLE, 0, 0},
        {CKA_LOCAL, 0, 0},
        {CKA_NEVER_EXTRACTABLE, 0, 0},
        {CKA_ALWAYS_SENSITIVE, 0, 0},
        {CKA_KEY_GEN_MECHANISM, 0, 0},
        {CKA_MODIFIABLE, 0, 0},
        {CKA_ECDSA_PARAMS, 0, 0},
        {CKA_EC_PARAMS, 0, 0},
        {CKA_EC_POINT, 0, 0},
        {CKA_SECONDARY_AUTH, 0, 0},
        {CKA_AUTH_PIN_FLAGS, 0, 0},
        {CKA_HW_FEATURE_TYPE, 0, 0},
        {CKA_RESET_ON_INIT, 0, 0},
        {CKA_HAS_RESET, 0, 0},
        {CKA_VENDOR_DEFINED, 0, 0},
        {CKA_NETSCAPE_DB, 0, 0},
};

/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    getNativeKeyInfo
 * Signature: (JJJLsun/security/pkcs11/wrapper/CK_MECHANISM;)[B
 * Parametermapping:                         *PKCS11*
 * @param   jlong         jSessionHandle     CK_SESSION_HANDLE hSession
 * @param   jlong         jKeyHandle         CK_OBJECT_HANDLE hObject
 * @param   jlong         jWrappingKeyHandle CK_OBJECT_HANDLE hObject
 * @param   jobject       jWrappingMech      CK_MECHANISM_PTR pMechanism
 * @return  jbyteArray    jNativeKeyInfo     -
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_getNativeKeyInfo
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jlong jKeyHandle,
    jlong jWrappingKeyHandle, jobject jWrappingMech)
{
    jbyteArray returnValue = NULL;
    CK_SESSION_HANDLE ckSessionHandle = jLongToCKULong(jSessionHandle);
    CK_OBJECT_HANDLE ckObjectHandle = jLongToCKULong(jKeyHandle);
    CK_ATTRIBUTE_PTR ckpAttributes = NULL;
    CK_RV rv;
    jbyteArray nativeKeyInfoArray = NULL;
    jbyteArray nativeKeyInfoWrappedKeyArray = NULL;
    jbyte* nativeKeyInfoArrayRaw = NULL;
    jbyte* nativeKeyInfoWrappedKeyArrayRaw = NULL;
    unsigned int sensitiveAttributePosition = (unsigned int)-1;
    unsigned int i = 0U;
    unsigned long totalDataSize = 0UL, attributesCount = 0UL;
    unsigned long totalCkAttributesSize = 0UL, totalNativeKeyInfoArraySize = 0UL;
    jbyte* wrappedKeySizePtr = NULL;
    jbyte* nativeKeyInfoArrayRawCkAttributes = NULL;
    jbyte* nativeKeyInfoArrayRawCkAttributesPtr = NULL;
    jbyte* nativeKeyInfoArrayRawDataPtr = NULL;
    CK_MECHANISM_PTR ckpMechanism = NULL;
    CK_ULONG ckWrappedKeyLength = 0U;
    jbyte* wrappedKeySizeWrappedKeyArrayPtr = NULL;
    CK_BYTE_PTR wrappedKeyBufferPtr = NULL;
    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    CK_OBJECT_CLASS class;
    CK_KEY_TYPE keyType;
    CK_BBOOL sensitive;
    CK_BBOOL netscapeAttributeValueNeeded = CK_FALSE;
    CK_ATTRIBUTE ckNetscapeAttributesTemplate[4];
    ckNetscapeAttributesTemplate[0].type = CKA_CLASS;
    ckNetscapeAttributesTemplate[1].type = CKA_KEY_TYPE;
    ckNetscapeAttributesTemplate[2].type = CKA_SENSITIVE;
    ckNetscapeAttributesTemplate[3].type = CKA_NETSCAPE_DB;
    ckNetscapeAttributesTemplate[0].pValue = &class;
    ckNetscapeAttributesTemplate[1].pValue = &keyType;
    ckNetscapeAttributesTemplate[2].pValue = &sensitive;
    ckNetscapeAttributesTemplate[3].pValue = 0;
    ckNetscapeAttributesTemplate[0].ulValueLen = sizeof(class);
    ckNetscapeAttributesTemplate[1].ulValueLen = sizeof(keyType);
    ckNetscapeAttributesTemplate[2].ulValueLen = sizeof(sensitive);
    ckNetscapeAttributesTemplate[3].ulValueLen = 0;

    if (ckpFunctions == NULL) { goto cleanup; }

    // If key is private and of DSA or EC type, NSS may require CKA_NETSCAPE_DB
    // attribute to unwrap it.
    rv = (*ckpFunctions->C_GetAttributeValue)(ckSessionHandle, ckObjectHandle,
            ckNetscapeAttributesTemplate,
            sizeof(ckNetscapeAttributesTemplate)/sizeof(CK_ATTRIBUTE));

    if (rv == CKR_OK && class == CKO_PRIVATE_KEY &&
            (keyType == CKK_EC || keyType == CKK_DSA) &&
            sensitive == CK_TRUE &&
            ckNetscapeAttributesTemplate[3].ulValueLen == CK_UNAVAILABLE_INFORMATION) {
        // We cannot set the attribute through C_SetAttributeValue here
        // because it might be read-only. However, we can add it to
        // the extracted buffer.
        netscapeAttributeValueNeeded = CK_TRUE;
        TRACE0("DEBUG: override CKA_NETSCAPE_DB attr value to TRUE\n");
    }

    ckpAttributes = (CK_ATTRIBUTE_PTR) calloc(
            CK_ATTRIBUTES_TEMPLATE_LENGTH, sizeof(CK_ATTRIBUTE));
    if (ckpAttributes == NULL) {
        throwOutOfMemoryError(env, 0);
        goto cleanup;
    }
    memcpy(ckpAttributes, ckpAttributesTemplate,
            CK_ATTRIBUTES_TEMPLATE_LENGTH * sizeof(CK_ATTRIBUTE));

    // Get sizes for value buffers
    // NOTE: may return an error code but length values are filled anyways
    (*ckpFunctions->C_GetAttributeValue)(ckSessionHandle, ckObjectHandle,
            ckpAttributes, CK_ATTRIBUTES_TEMPLATE_LENGTH);

    for (i = 0; i < CK_ATTRIBUTES_TEMPLATE_LENGTH; i++) {
        if ((ckpAttributes+i)->ulValueLen != CK_UNAVAILABLE_INFORMATION) {
            totalDataSize += (ckpAttributes+i)->ulValueLen;
            if ((ckpAttributes+i)->type == CKA_SENSITIVE) {
                 sensitiveAttributePosition = attributesCount;
                 TRACE0("DEBUG: GetNativeKeyInfo key is sensitive");
            }
            attributesCount++;
        }
    }

    if (netscapeAttributeValueNeeded) {
        attributesCount++;
    }

    // Allocate a single buffer to hold valid attributes and attribute's values
    // Buffer structure: [ attributes-size, [ ... attributes ... ],
    //                   values-size, [ ... values ... ], wrapped-key-size,
    //                   [ ... wrapped-key ... ] ]
    //     * sizes are expressed in bytes and data type is unsigned long
    totalCkAttributesSize = attributesCount * sizeof(CK_ATTRIBUTE);
    TRACE1("DEBUG: GetNativeKeyInfo attributesCount = %lu\n", attributesCount);
    TRACE1("DEBUG: GetNativeKeyInfo sizeof CK_ATTRIBUTE = %zu\n", sizeof(CK_ATTRIBUTE));
    TRACE1("DEBUG: GetNativeKeyInfo totalCkAttributesSize = %lu\n", totalCkAttributesSize);
    TRACE1("DEBUG: GetNativeKeyInfo totalDataSize = %lu\n", totalDataSize);

    totalNativeKeyInfoArraySize =
            totalCkAttributesSize + sizeof(unsigned long) * 3 + totalDataSize;

    TRACE1("DEBUG: GetNativeKeyInfo totalNativeKeyInfoArraySize = %lu\n", totalNativeKeyInfoArraySize);

    nativeKeyInfoArray = (*env)->NewByteArray(env, totalNativeKeyInfoArraySize);
    if (nativeKeyInfoArray == NULL) {
        goto cleanup;
    }

    nativeKeyInfoArrayRaw = (*env)->GetByteArrayElements(env, nativeKeyInfoArray,
            NULL);
    if (nativeKeyInfoArrayRaw == NULL) {
        goto cleanup;
    }

    wrappedKeySizePtr = nativeKeyInfoArrayRaw +
            sizeof(unsigned long)*2 + totalCkAttributesSize + totalDataSize;
    memcpy(nativeKeyInfoArrayRaw, &totalCkAttributesSize, sizeof(unsigned long));

    memcpy(nativeKeyInfoArrayRaw + sizeof(unsigned long) + totalCkAttributesSize,
        &totalDataSize, sizeof(unsigned long));

    memset(wrappedKeySizePtr, 0, sizeof(unsigned long));

    nativeKeyInfoArrayRawCkAttributes = nativeKeyInfoArrayRaw +
            sizeof(unsigned long);
    nativeKeyInfoArrayRawCkAttributesPtr = nativeKeyInfoArrayRawCkAttributes;
    nativeKeyInfoArrayRawDataPtr = nativeKeyInfoArrayRaw +
            totalCkAttributesSize + sizeof(unsigned long) * 2;

    for (i = 0; i < CK_ATTRIBUTES_TEMPLATE_LENGTH; i++) {
        if ((ckpAttributes+i)->ulValueLen != CK_UNAVAILABLE_INFORMATION) {
            (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).type =
                    (ckpAttributes+i)->type;
            (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).ulValueLen =
                    (ckpAttributes+i)->ulValueLen;
            if ((ckpAttributes+i)->ulValueLen != 0) {
                (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).pValue =
                        nativeKeyInfoArrayRawDataPtr;
            } else {
                (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).pValue = 0;
            }
            nativeKeyInfoArrayRawDataPtr +=
                    (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).ulValueLen;
            nativeKeyInfoArrayRawCkAttributesPtr += sizeof(CK_ATTRIBUTE);
        }
    }

    TRACE0("DEBUG: GetNativeKeyInfo finished prepping nativeKeyInfoArray\n");

    // Get attribute's values
    rv = (*ckpFunctions->C_GetAttributeValue)(ckSessionHandle, ckObjectHandle,
            (CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributes,
            attributesCount);
    if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) {
        goto cleanup;
    }

    TRACE0("DEBUG: GetNativeKeyInfo 1st C_GetAttributeValue call passed\n");

    if (netscapeAttributeValueNeeded) {
        (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).type = CKA_NETSCAPE_DB;
        // Value is not needed, public key is not used
    }

    if ((sensitiveAttributePosition != (unsigned int)-1) &&
        *(CK_BBOOL*)(((CK_ATTRIBUTE_PTR)(((CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributes)
                +sensitiveAttributePosition))->pValue) == CK_TRUE) {
        // Key is sensitive. Need to extract it wrapped.
        if (jWrappingKeyHandle != 0) {

            ckpMechanism = jMechanismToCKMechanismPtr(env, jWrappingMech);
            rv = (*ckpFunctions->C_WrapKey)(ckSessionHandle, ckpMechanism,
                    jLongToCKULong(jWrappingKeyHandle), ckObjectHandle,
                    NULL_PTR, &ckWrappedKeyLength);
            if (ckWrappedKeyLength != 0) {
                // Allocate space for getting the wrapped key
                nativeKeyInfoWrappedKeyArray = (*env)->NewByteArray(env,
                        totalNativeKeyInfoArraySize + ckWrappedKeyLength);
                if (nativeKeyInfoWrappedKeyArray == NULL) {
                    goto cleanup;
                }
                nativeKeyInfoWrappedKeyArrayRaw =
                        (*env)->GetByteArrayElements(env,
                                nativeKeyInfoWrappedKeyArray, NULL);
                if (nativeKeyInfoWrappedKeyArrayRaw == NULL) {
                    goto cleanup;
                }
                memcpy(nativeKeyInfoWrappedKeyArrayRaw, nativeKeyInfoArrayRaw,
                        totalNativeKeyInfoArraySize);
                wrappedKeySizeWrappedKeyArrayPtr =
                        nativeKeyInfoWrappedKeyArrayRaw +
                        sizeof(unsigned long)*2 + totalCkAttributesSize +
                        totalDataSize;
                memcpy(wrappedKeySizeWrappedKeyArrayPtr, &ckWrappedKeyLength, sizeof(unsigned long));
                TRACE1("DEBUG: GetNativeKeyInfo 1st C_WrapKey wrappedKeyLength = %lu\n", ckWrappedKeyLength);

                wrappedKeyBufferPtr =
                        (CK_BYTE_PTR) (wrappedKeySizeWrappedKeyArrayPtr +
                        sizeof(unsigned long));
                rv = (*ckpFunctions->C_WrapKey)(ckSessionHandle, ckpMechanism,
                        jLongToCKULong(jWrappingKeyHandle),ckObjectHandle,
                        wrappedKeyBufferPtr, &ckWrappedKeyLength);
                if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) {
                    goto cleanup;
                }
                memcpy(wrappedKeySizeWrappedKeyArrayPtr, &ckWrappedKeyLength, sizeof(unsigned long));
                TRACE1("DEBUG: GetNativeKeyInfo 2nd C_WrapKey wrappedKeyLength = %lu\n", ckWrappedKeyLength);
            } else {
                goto cleanup;
            }
        } else {
            ckAssertReturnValueOK(env, CKR_KEY_HANDLE_INVALID);
            goto cleanup;
        }
        returnValue = nativeKeyInfoWrappedKeyArray;
    } else {
        returnValue = nativeKeyInfoArray;
    }

cleanup:
    if (ckpAttributes != NULL) {
        free(ckpAttributes);
    }

    if (nativeKeyInfoArrayRaw != NULL) {
        (*env)->ReleaseByteArrayElements(env, nativeKeyInfoArray,
                nativeKeyInfoArrayRaw, 0);
    }

    if (nativeKeyInfoWrappedKeyArrayRaw != NULL) {
        (*env)->ReleaseByteArrayElements(env, nativeKeyInfoWrappedKeyArray,
                nativeKeyInfoWrappedKeyArrayRaw, 0);
    }

    if (nativeKeyInfoArray != NULL && returnValue != nativeKeyInfoArray) {
        (*env)->DeleteLocalRef(env, nativeKeyInfoArray);
    }

    if (nativeKeyInfoWrappedKeyArray != NULL
            && returnValue != nativeKeyInfoWrappedKeyArray) {
        (*env)->DeleteLocalRef(env, nativeKeyInfoWrappedKeyArray);
    }
    freeCKMechanismPtr(ckpMechanism);

    return returnValue;
}
#endif

#ifdef P11_ENABLE_CREATENATIVEKEY
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    createNativeKey
 * Signature: (J[BJLsun/security/pkcs11/wrapper/CK_MECHANISM;)J
 * Parametermapping:                          *PKCS11*
 * @param   jlong         jSessionHandle      CK_SESSION_HANDLE hSession
 * @param   jbyteArray    jNativeKeyInfo      -
 * @param   jlong         jWrappingKeyHandle  CK_OBJECT_HANDLE hObject
 * @param   jobject       jWrappingMech       CK_MECHANISM_PTR pMechanism
 * @return  jlong         jKeyHandle          CK_OBJECT_HANDLE hObject
 */
JNIEXPORT jlong JNICALL
Java_sun_security_pkcs11_wrapper_PKCS11_createNativeKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jbyteArray jNativeKeyInfo,
    jlong jWrappingKeyHandle, jobject jWrappingMech)
{
    CK_OBJECT_HANDLE ckObjectHandle;
    CK_RV rv;
    CK_SESSION_HANDLE ckSessionHandle = jLongToCKULong(jSessionHandle);
    jbyte* nativeKeyInfoArrayRaw = NULL;
    jlong jObjectHandle = 0L;
    unsigned long totalCkAttributesSize = 0UL;
    unsigned long nativeKeyInfoCkAttributesCount = 0UL;
    jbyte* nativeKeyInfoArrayRawCkAttributes = NULL;
    jbyte* nativeKeyInfoArrayRawCkAttributesPtr = NULL;
    jbyte* nativeKeyInfoArrayRawDataPtr = NULL;
    unsigned long totalDataSize = 0UL;
    jbyte* wrappedKeySizePtr = NULL;
    unsigned int i = 0U;
    CK_MECHANISM_PTR ckpMechanism = NULL;
    CK_ULONG ckWrappedKeyLength = 0UL;
    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);

    if (ckpFunctions == NULL) { goto cleanup; }

    nativeKeyInfoArrayRaw =
            (*env)->GetByteArrayElements(env, jNativeKeyInfo, NULL);
    if (nativeKeyInfoArrayRaw == NULL) {
        goto cleanup;
    }

    memcpy(&totalCkAttributesSize, nativeKeyInfoArrayRaw, sizeof(unsigned long));
    TRACE1("DEBUG: createNativeKey totalCkAttributesSize = %lu\n", totalCkAttributesSize);
    nativeKeyInfoCkAttributesCount = totalCkAttributesSize/sizeof(CK_ATTRIBUTE);
    TRACE1("DEBUG: createNativeKey nativeKeyInfoCkAttributesCount = %lu\n", nativeKeyInfoCkAttributesCount);

    nativeKeyInfoArrayRawCkAttributes = nativeKeyInfoArrayRaw +
            sizeof(unsigned long);
    nativeKeyInfoArrayRawCkAttributesPtr = nativeKeyInfoArrayRawCkAttributes;
    nativeKeyInfoArrayRawDataPtr = nativeKeyInfoArrayRaw +
            totalCkAttributesSize + sizeof(unsigned long) * 2;
    memcpy(&totalDataSize, (nativeKeyInfoArrayRaw + totalCkAttributesSize + sizeof(unsigned long)),
            sizeof(unsigned long));
    TRACE1("DEBUG: createNativeKey totalDataSize = %lu\n", totalDataSize);

    wrappedKeySizePtr = nativeKeyInfoArrayRaw +
            sizeof(unsigned long)*2 + totalCkAttributesSize + totalDataSize;

    memcpy(&ckWrappedKeyLength, wrappedKeySizePtr, sizeof(unsigned long));
    TRACE1("DEBUG: createNativeKey wrappedKeyLength = %lu\n", ckWrappedKeyLength);

    for (i = 0; i < nativeKeyInfoCkAttributesCount; i++) {
        if ((*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).ulValueLen
                > 0) {
            (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).pValue =
                    nativeKeyInfoArrayRawDataPtr;
        }
        nativeKeyInfoArrayRawDataPtr +=
                (*(CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributesPtr).ulValueLen;
        nativeKeyInfoArrayRawCkAttributesPtr += sizeof(CK_ATTRIBUTE);
    }

    if (ckWrappedKeyLength == 0) {
        // Not a wrapped key
        rv = (*ckpFunctions->C_CreateObject)(ckSessionHandle,
                (CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributes,
                jLongToCKULong(nativeKeyInfoCkAttributesCount), &ckObjectHandle);
    } else {
        // Wrapped key
        ckpMechanism = jMechanismToCKMechanismPtr(env, jWrappingMech);
        rv = (*ckpFunctions->C_UnwrapKey)(ckSessionHandle, ckpMechanism,
                jLongToCKULong(jWrappingKeyHandle),
                (CK_BYTE_PTR)(wrappedKeySizePtr + sizeof(unsigned long)),
                ckWrappedKeyLength,
                (CK_ATTRIBUTE_PTR)nativeKeyInfoArrayRawCkAttributes,
                jLongToCKULong(nativeKeyInfoCkAttributesCount),
                &ckObjectHandle);
    }
    if (ckAssertReturnValueOK(env, rv) != CK_ASSERT_OK) {
        goto cleanup;
    }

    jObjectHandle = ckULongToJLong(ckObjectHandle);

cleanup:

    if (nativeKeyInfoArrayRaw != NULL) {
        (*env)->ReleaseByteArrayElements(env, jNativeKeyInfo,
                nativeKeyInfoArrayRaw, JNI_ABORT);
    }

    freeCKMechanismPtr(ckpMechanism);
    return jObjectHandle;
}
#endif

#ifdef P11_ENABLE_C_GENERATEKEY
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_GenerateKey
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)J
 * Parametermapping:                    *PKCS11*
 * @param   jlong jSessionHandle        CK_SESSION_HANDLE hSession
 * @param   jobject jMechanism          CK_MECHANISM_PTR pMechanism
 * @param   jobjectArray jTemplate      CK_ATTRIBUTE_PTR pTemplate
 *                                      CK_ULONG ulCount
 * @return  jlong jKeyHandle            CK_OBJECT_HANDLE_PTR phKey
 */
JNIEXPORT jlong JNICALL Java_sun_security_pkcs11_wrapper_PKCS11_C_1GenerateKey
    (JNIEnv *env, jobject obj, jlong jSessionHandle, jobject jMechanism, jobjectArray jTemplate)
{
    CK_SESSION_HANDLE ckSessionHandle;
    CK_MECHANISM_PTR ckpMechanism = NULL;
    CK_ATTRIBUTE_PTR ckpAttributes = NULL_PTR;
    CK_ULONG ckAttributesLength = 0;
    CK_OBJECT_HANDLE ckKeyHandle = 0;
    jlong jKeyHandle = 0L;
    CK_RV rv;

    CK_FUNCTION_LIST_PTR ckpFunctions = getFunctionList(env, obj);
    if (ckpFunctions == NULL) { return 0L; }

    ckSessionHandle = jLongToCKULong(jSessionHandle);
    ckpMechanism = jMechanismToCKMechanismPtr(env, jMechanism);
    if ((*env)->ExceptionCheck(env)) { return 0L ; }

    jAttributeArrayToCKAttributeArray(env, jTemplate, &ckpAttributes, &ckAttributesLength);
    if ((*env)->ExceptionCheck(env)) {
        goto cleanup;
    }

    rv = (*ckpFunctions->C_GenerateKey)(ckSessionHandle, ckpMechanism, ckpAttributes, ckAttributesLength, &ckKeyHandle);

    if (ckAssertReturnValueOK(env, rv) == CK_ASSERT_OK) {
        jKeyHandle = ckULongToJLong(ckKeyHandle);

        /* cheack, if we must give a initialization vector back to Java */
        switch (ckpMechanism->mechanism) {
        case CKM_PBE_MD2_DES_CBC:
        case CKM_PBE_MD5_DES_CBC:
        case CKM_PBE_MD5_CAST_CBC:
        case CKM_PBE_MD5_CAST3_CBC:
        case CKM_PBE_MD5_CAST128_CBC:
        /* case CKM_PBE_MD5_CAST5_CBC:  the same as CKM_PBE_MD5_CAST128_CBC */
        case CKM_PBE_SHA1_CAST128_CBC:
        /* case CKM_PBE_SHA1_CAST5_CBC: the same as CKM_PBE_SHA1_CAST128_CBC */
            /* we must copy back the initialization vector to the jMechanism object */
            copyBackPBEInitializationVector(env, ckpMechanism, jMechanism);
            break;
        }
    }
cleanup:
    freeCKMechanismPtr(ckpMechanism);
    freeCKAttributeArray(ckpAttributes, ckAttributesLength);

    return jKeyHandle ;
}
#endif

#ifdef P11_ENABLE_C_GENERATEKEYPAIR
/*
 * Class:     sun_security_pkcs11_wrapper_PKCS11
 * Method:    C_GenerateKeyPair
 * Signature: (JLsun/security/pkcs11/wrapper/CK_MECHANISM;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;[Lsun/security/pkcs11/wrapper/CK_ATTRIBUTE;)[J
 * 