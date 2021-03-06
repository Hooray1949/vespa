// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "routablefactories50.h"

namespace document { class DocumentTypeRepo; }

namespace documentapi {
/**
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement factories for the document
 * routable. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 */
class RoutableFactories51 : public RoutableFactories50 {
public:
    RoutableFactories51() = delete;

    class DocumentIgnoredReplyFactory : public DocumentReplyFactory {
    protected:
        DocumentReply::UP doDecode(document::ByteBuffer &buf) const override;
        bool doEncode(const DocumentReply &reply, vespalib::GrowableByteBuffer &buf) const override;
    };

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Factories
    //
    ////////////////////////////////////////////////////////////////////////////////

    class CreateVisitorMessageFactory : public DocumentMessageFactory {
        const document::DocumentTypeRepo &_repo;
    protected:
        DocumentMessage::UP doDecode(document::ByteBuffer &buf) const override;
        bool doEncode(const DocumentMessage &msg, vespalib::GrowableByteBuffer &buf) const override;

        virtual bool encodeBucketSpace(vespalib::stringref bucketSpace, vespalib::GrowableByteBuffer& buf) const;
        virtual string decodeBucketSpace(document::ByteBuffer&) const;
    public:
        CreateVisitorMessageFactory(const document::DocumentTypeRepo &r) : _repo(r) {}
    };

    class GetDocumentMessageFactory : public DocumentMessageFactory {
    protected:
        DocumentMessage::UP doDecode(document::ByteBuffer &buf) const override;
        bool doEncode(const DocumentMessage &msg, vespalib::GrowableByteBuffer &buf) const override;
    };

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ///////////////////////////////////////////////////////////////////////////
 protected:
    /**
     * This is a complement for the vespalib::GrowableByteBuffer.putString() method.
     *
     * @param in The byte buffer to read from.
     * @return The decoded string.
     */
    static string decodeString(document::ByteBuffer &in)
    { return RoutableFactories50::decodeString(in); }

    /**
     * This is a complement for the vespalib::GrowableByteBuffer.putBoolean() method.
     *
     * @param in The byte buffer to read from.
     * @return The decoded bool.
     */
    static bool decodeBoolean(document::ByteBuffer &in)
    { return RoutableFactories50::decodeBoolean(in); }

    /**
     * Convenience method to decode a 32-bit int from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded int.
     */
    static int32_t decodeInt(document::ByteBuffer &in)
    { return RoutableFactories50::decodeInt(in); }

    /**
     * Convenience method to decode a 64-bit int from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded int.
     */
    static int64_t decodeLong(document::ByteBuffer &in)
    { return RoutableFactories50::decodeLong(in); }


    /**
     * Convenience method to decode a document id from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded document id.
     */
    static document::DocumentId decodeDocumentId(document::ByteBuffer &in)
    { return RoutableFactories50::decodeDocumentId(in); }

    /**
     * Convenience method to encode a document id to the given byte buffer.
     *
     * @param id  The document id to encode.
     * @param out The byte buffer to write to.
     */
    static void encodeDocumentId(const document::DocumentId &id,
                                 vespalib::GrowableByteBuffer &out)
    { return RoutableFactories50::encodeDocumentId(id, out); }
};

}

