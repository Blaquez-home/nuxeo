/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Salem Aouana
 */

package org.nuxeo.ecm.core;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ecm.core.DummyThumbnailFactory.DUMMY_THUMBNAIL_CONTENT;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.COLD_STORAGE_BEING_RETRIEVED_PROPERTY;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.COLD_STORAGE_CONTENT_AVAILABLE_EVENT_NAME;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.COLD_STORAGE_CONTENT_PROPERTY;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.ColdStorageContentStatus;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.FILE_CONTENT_PROPERTY;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.checkAvailabilityOfColdStorageContent;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.moveContentToColdStorage;
import static org.nuxeo.ecm.core.blob.ColdStorageHelper.retrieveContentFromColdStorage;
import static org.nuxeo.ecm.core.schema.FacetNames.COLD_STORAGE;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-coldstorage-contrib.xml")
public class TestColdStorage {

    protected static final String FILE_CONTENT = "foo";

    protected static final int DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY = 5;

    protected static final String DEFAULT_DOC_NAME = "anyFile";

    @Inject
    protected CoreSession session;

    @Test
    public void shouldMoveBlobDocumentToColdStorage() throws IOException {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, true);

        // move the blob to cold storage
        documentModel = moveContentToColdStorage(session, documentModel.getRef());
        session.save();
        assertTrue(documentModel.hasFacet(COLD_STORAGE));

        // check if the `file:content` contains the thumbnail blob
        checkBlobContent(documentModel, FILE_CONTENT_PROPERTY, DUMMY_THUMBNAIL_CONTENT);

        // check if the `coldstorage:coldContent` contains the original file content
        checkBlobContent(documentModel, COLD_STORAGE_CONTENT_PROPERTY, FILE_CONTENT);
    }

    @Test
    public void shouldFailWhenMovingDocumentBlobAlreadyInColdStorage() {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, true);

        // move for the first time
        documentModel = moveContentToColdStorage(session, documentModel.getRef());

        // try to make another move
        try {
            moveContentToColdStorage(session, documentModel.getRef());
            fail("Should fail because the content is already in cold storage");
        } catch (NuxeoException ne) {
            assertEquals(SC_CONFLICT, ne.getStatusCode());
            assertEquals(String.format("The main content for document: %s is already in cold storage.", documentModel),
                    ne.getMessage());
        }
    }

    @Test
    public void shouldFailWhenMovingToColdStorageDocumentWithoutContent() {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, false);
        try {
            moveContentToColdStorage(session, documentModel.getRef());
            fail("Should fail because there is no main content associated with the document");
        } catch (NuxeoException ne) {
            assertEquals(SC_NOT_FOUND, ne.getStatusCode());
            assertEquals(String.format("There is no main content for document: %s.", documentModel), ne.getMessage());
        }
    }

    @Test
    public void shouldRetrieveDocumentBlobFromColdStorage() throws IOException {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, true);

        // move the blob to cold storage
        moveContentToColdStorage(session, documentModel.getRef());

        // retrieve, which means initiate a request to restore the blob from cold storage
        documentModel = retrieveContentFromColdStorage(session, documentModel.getRef(),
                DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY);
        session.save();
        assertTrue((Boolean) documentModel.getPropertyValue(COLD_STORAGE_BEING_RETRIEVED_PROPERTY));

        // check that `file:content` still contains the thumbnail blob
        checkBlobContent(documentModel, FILE_CONTENT_PROPERTY, DUMMY_THUMBNAIL_CONTENT);

        // check that `coldstorage:coldContent` still contains the original file content
        checkBlobContent(documentModel, COLD_STORAGE_CONTENT_PROPERTY, FILE_CONTENT);
    }

    @Test
    public void shouldFailWhenRetrievingDocumentBlobFromColdStorageBeingRetrieved() {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, true);

        // move the blob to cold storage
        moveContentToColdStorage(session, documentModel.getRef());

        // retrieve, which means initiate a request to restore the blob from cold storage
        documentModel = retrieveContentFromColdStorage(session, documentModel.getRef(),
                DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY);

        // try to retrieve a second time
        try {
            retrieveContentFromColdStorage(session, documentModel.getRef(), DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY);
            fail("Should fail because the cold storage content is being retrieved.");
        } catch (NuxeoException ne) {
            assertEquals(SC_CONFLICT, ne.getStatusCode());
            assertEquals(String.format("The cold storage content associated with the document: %s is being retrieved.",
                    documentModel), ne.getMessage());
        }
    }

    @Test
    public void shouldFailWhenRetrievingDocumentBlobWithoutColdStorageContent() {
        DocumentModel documentModel = createDocument(DEFAULT_DOC_NAME, true);
        try {
            // try to retrieve from cold storage where the blob is not stored in it
            retrieveContentFromColdStorage(session, documentModel.getRef(), DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY);
            fail("Should fail because there no cold storage content associated to this document.");
        } catch (NuxeoException ne) {
            assertEquals(SC_NOT_FOUND, ne.getStatusCode());
            assertEquals(String.format("No cold storage content defined for document: %s.", documentModel),
                    ne.getMessage());
        }
    }

    @Test
    public void shouldCheckAvailabilityOfColdStorageContent() {
        List<String> documents = Arrays.asList( //
                moveAndRetrieveColdStorageContent(DEFAULT_DOC_NAME).getId(),
                moveAndRetrieveColdStorageContent("anyFile2").getId(), //
                moveAndRetrieveColdStorageContent("anyFile3").getId());
        session.save();
        try (CapturingEventListener listener = new CapturingEventListener(COLD_STORAGE_CONTENT_AVAILABLE_EVENT_NAME)) {
            ColdStorageContentStatus coldStorageContentStatus = checkAvailabilityOfColdStorageContent(session);
            assertEquals(session.getRepositoryName(), coldStorageContentStatus.getRepositoryName());

            // documents.size()/documents.size() available
            assertEquals(documents.size(), coldStorageContentStatus.getTotalBeingRetrieved());
            assertEquals(documents.size(), coldStorageContentStatus.getTotalContentAvailable());

            assertTrue(listener.hasBeenFired(COLD_STORAGE_CONTENT_AVAILABLE_EVENT_NAME));
            assertEquals(3, listener.streamCapturedEvents().count());

            List<String> docEvents = listener.streamCapturedEvents().map(event -> {
                DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
                return docCtx.getSourceDocument().getId();
            }).sorted().collect(Collectors.toList());

            documents.sort(Comparator.naturalOrder());
            assertEquals(documents, docEvents);
        }
    }

    protected DocumentModel moveAndRetrieveColdStorageContent(String documentName) {
        DocumentModel documentModel = createDocument(documentName, true);
        moveContentToColdStorage(session, documentModel.getRef());
        retrieveContentFromColdStorage(session, documentModel.getRef(), DEFAULT_NUMBER_OF_DAYS_OF_AVAILABILITY);
        return documentModel;
    }

    protected DocumentModel createDocument(String name, boolean addBlobContent) {
        DocumentModel documentModel = session.createDocumentModel("/", name, "File");
        if (addBlobContent) {
            documentModel.setPropertyValue("file:content", (Serializable) Blobs.createBlob(FILE_CONTENT));
        }
        return session.createDocument(documentModel);
    }

    protected void checkBlobContent(DocumentModel documentModel, String xpath, String expectedContent)
            throws IOException {
        Blob content = (Blob) documentModel.getPropertyValue(xpath);
        assertNotNull(content);
        assertEquals(expectedContent, content.getString());
    }
}