/*
 * (C) Copyright IBM Corp. 2020, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.server.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.linuxforhealth.fhir.config.FHIRConfiguration;
import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.core.FHIRVersionParam;
import org.linuxforhealth.fhir.core.ResourceType;
import org.linuxforhealth.fhir.exception.FHIRException;
import org.linuxforhealth.fhir.model.resource.CapabilityStatement;
import org.linuxforhealth.fhir.model.resource.CapabilityStatement.Rest.Resource.Interaction;
import org.linuxforhealth.fhir.search.util.SearchHelper;
import org.linuxforhealth.fhir.server.resources.Capabilities;

public class CapabilitiesTest {
    private final boolean DEBUG = false;

    SearchHelper searchHelper = new SearchHelper();

    @BeforeClass
    void setup() {
        FHIRConfiguration.setConfigHome("target/test-classes");
    }

    @AfterClass
    void tearDown() throws FHIRException {
        FHIRConfiguration.setConfigHome("");
        FHIRRequestContext.get().setTenantId("default");
    }

    @Test
    void testBuildCapabilityStatement_resources_omitted() throws Exception {
        FHIRRequestContext.get().setTenantId("omitted");
        FHIRRequestContext.get().setOriginalRequestUri("http://example.com/metadata");
        CapabilitiesChild c = new CapabilitiesChild(searchHelper, FHIRVersionParam.VERSION_40);

        Response capabilities = c.capabilities("full");
        CapabilityStatement capabilityStatement = capabilities.readEntity(CapabilityStatement.class);

        assertEquals(capabilityStatement.getRest().size(), 1, "Number of REST Elements");
        CapabilityStatement.Rest restDefinition = capabilityStatement.getRest().get(0);

        assertRestDefinition(restDefinition, 4, 126, 9, 1, 1, 9, 1, 1);
    }

    @Test
    void testBuildCapabilityStatement_resources_empty_r4() throws Exception {
        FHIRRequestContext.get().setTenantId("empty");
        FHIRRequestContext.get().setOriginalRequestUri("http://example.com/metadata");
        CapabilitiesChild c = new CapabilitiesChild(searchHelper, FHIRVersionParam.VERSION_40);

        Response capabilities = c.capabilities("full");
        CapabilityStatement capabilityStatement = capabilities.readEntity(CapabilityStatement.class);

        assertEquals(capabilityStatement.getRest().size(), 1, "Number of REST Elements");
        CapabilityStatement.Rest restDefinition = capabilityStatement.getRest().get(0);

        // batch and transaction
        assertRestDefinition(restDefinition, 2, 126, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testBuildCapabilityStatement_resources_empty_r4b() throws Exception {
        FHIRRequestContext.get().setTenantId("empty");
        FHIRRequestContext.get().setOriginalRequestUri("http://example.com/metadata");
        CapabilitiesChild c = new CapabilitiesChild(searchHelper, FHIRVersionParam.VERSION_43);

        Response capabilities = c.capabilities("full");
        CapabilityStatement capabilityStatement = capabilities.readEntity(CapabilityStatement.class);

        assertEquals(capabilityStatement.getRest().size(), 1, "Number of REST Elements");
        CapabilityStatement.Rest restDefinition = capabilityStatement.getRest().get(0);

        assertRestDefinition(restDefinition, 2, 141, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testBuildCapabilityStatement_resources_filtered() throws Exception {
        FHIRRequestContext.get().setTenantId("smart-enabled");
        FHIRRequestContext.get().setOriginalRequestUri("http://example.com/metadata");
        CapabilitiesChild c = new CapabilitiesChild(searchHelper, FHIRVersionParam.VERSION_43);

        Response capabilities = c.capabilities("full");
        CapabilityStatement capabilityStatement = capabilities.readEntity(CapabilityStatement.class);

        assertEquals(capabilityStatement.getRest().size(), 1, "Number of REST Elements");
        CapabilityStatement.Rest restDefinition = capabilityStatement.getRest().get(0);

        assertRestDefinition(restDefinition, 4, 2, 2, 1, 1, 5, 1, 1);
    }

    private void assertRestDefinition(CapabilityStatement.Rest restDefinition, int systemInteractions, int numOfResources,
            int patientInteractions, int patientIncludes, int patientRevIncludes,
            int practitionerInteractions, int practitionerIncludes, int practitionerRevIncludes) {
        if (DEBUG) {
            System.out.println(restDefinition);
        }
        assertEquals(restDefinition.getInteraction().size(), systemInteractions, "Number of supported system-level interactions");
        assertEquals(restDefinition.getResource().size(), numOfResources, "Number of supported resources");
        assertFalse(restDefinition.getResource().stream().anyMatch(r -> r.getType().getValueAsEnum() == ResourceType.RESOURCE));
        assertFalse(restDefinition.getResource().stream().anyMatch(r -> r.getType().getValueAsEnum() == ResourceType.DOMAIN_RESOURCE));

        assertResourceDefinition(restDefinition, ResourceType.PATIENT, patientInteractions, patientIncludes, patientRevIncludes);
        assertResourceDefinition(restDefinition, ResourceType.PRACTITIONER, practitionerInteractions, practitionerIncludes, practitionerRevIncludes);
    }

    private void assertResourceDefinition(CapabilityStatement.Rest restDefinition, ResourceType resourceType, int numOfInteractions,
            int numIncludes, int numRevIncludes) {
        Optional<CapabilityStatement.Rest.Resource> resource = restDefinition.getResource().stream()
                .filter(r -> r.getType().getValueAsEnum() == resourceType)
                .findFirst();
        assertTrue(resource.isPresent());

        List<Interaction> interactions = resource.get().getInteraction();
        assertEquals(interactions.size(), numOfInteractions, "Number of supported interactions for the " + resourceType + " resource type");
        List<org.linuxforhealth.fhir.model.type.String> includes = resource.get().getSearchInclude();
        assertEquals(includes.size(), numIncludes, "Number of supported search includes for the " + resourceType + " resource type");
        List<org.linuxforhealth.fhir.model.type.String> revIncludes = resource.get().getSearchRevInclude();
        assertEquals(revIncludes.size(), numRevIncludes, "Number of supported search revincludes for the " + resourceType + " resource type");
    }

    /**
     * This class is required because Capabilities uses a few protected fields
     * that are normally injected by JAX-RS and so this is the only way to set them.
     */
    private static class CapabilitiesChild extends Capabilities {
        /**
         * @implNote Under "normal" operation, the FHIRVersionParam is set via the
         *          FHIRVersionRequestFilter. To simulate that, use a different
         *          CapabilitesChild for each request with a new fhirVersion value
         */
        public CapabilitiesChild(SearchHelper searchHelper, FHIRVersionParam fhirVersion) throws Exception {
            super();
            this.context = new MockServletContext();
            this.searchHelper = searchHelper;
            this.httpServletRequest = new MockHttpServletRequest(fhirVersion);
        }
    }
}
