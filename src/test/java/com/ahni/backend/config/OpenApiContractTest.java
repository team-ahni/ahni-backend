package com.ahni.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class OpenApiContractTest {
    @Test
    void adminLectureMutationsDocumentIdentityProtectionAndLinkedTimetableDeletion() throws Exception {
        var actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        var path = document.at("/paths/~1api~1v1~1admin~1lectures~1{entityId}");
        for (String method : new String[]{"put", "delete"}) {
            assertTrue(path.path(method).at("/security/0/bearerAuth").isArray());
            for (String code : new String[]{"400", "401", "403", "404"})
                assertTrue(path.path(method).path("responses").has(code));
        }
        assertTrue(path.at("/put/responses").has("200"));
        assertTrue(path.at("/put/responses").has("409"));
        assertTrue(path.at("/delete/responses").has("204"));
        assertEquals("#/components/schemas/LectureUpdateRequest", path.at("/put/requestBody/content/application~1json/schema/$ref").asText());
        assertEquals(0, document.at("/components/schemas/LectureUpdateRequest/properties/schedules/minItems").asInt());
        assertEquals(14, document.at("/components/schemas/LectureUpdateRequest/properties/schedules/maxItems").asInt());
    }

    @Test
    void lecturesUseInstructorTextWithoutProfessorResources() throws Exception {
        var actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        assertTrue(!document.path("paths").has("/api/v1/admin/professors"));
        assertTrue(!document.at("/components/schemas").has("ProfessorResponse"));
        assertTrue(document.at("/components/schemas/LectureRequest/required").toString().contains("professorName"));
        assertEquals(500, document.at("/components/schemas/LectureRequest/properties/professorName/maxLength").asInt());
        assertTrue(document.at("/components/schemas/LectureResponse/properties").has("professorName"));
        assertTrue(!document.at("/components/schemas/LectureResponse/properties").has("professor"));
    }

    @Test
    void lectureImportDocumentsAtomicAdminBatchAndArrayResponse() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        var operation = document.at("/paths/~1api~1v1~1admin~1lectures~1import/post");
        assertTrue(operation.at("/security/0/bearerAuth").isArray());
        for (String code : new String[]{"201", "400", "401", "403", "404", "409"})
            assertTrue(operation.path("responses").has(code));
        assertEquals("array", operation.at("/responses/201/content/application~1json/schema/type").asText());
        assertEquals(200, document.at("/components/schemas/LectureImportRequest/properties/lectures/maxItems").asInt());
    }

    @Test
    void yearlyCurriculumContractHasAuthenticatedYearQueryAndVersionedAdminWrites() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        var catalog = document.at("/paths/~1api~1v1~1curriculum-courses/get");
        assertTrue(catalog.at("/security/0/bearerAuth").isArray());
        assertEquals("array", catalog.at("/responses/200/content/application~1json/schema/type").asText());
        boolean yearRequired = false;
        for (var parameter : catalog.path("parameters")) if (parameter.path("name").asText().equals("academicYear")) yearRequired = parameter.path("required").asBoolean();
        assertTrue(yearRequired);
        var create = document.at("/paths/~1api~1v1~1admin~1curricula/post");
        for (var code : new String[]{"201", "400", "401", "403", "404", "409"}) assertTrue(create.path("responses").has(code));
        assertEquals("#/components/schemas/CurriculumResponse", create.at("/responses/201/content/application~1json/schema/$ref").asText());
        assertTrue(document.at("/components/schemas/CurriculumPublicationRequest/required").toString().contains("version"));
        assertTrue(document.at("/components/schemas/CurriculumCourseRequest/properties/division/enum").toString().contains("GENERAL_REQUIRED"));
    }

    @Test
    void curriculumWriteExamplesReflectSavedConnectionsAndPublicationVersion() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        var created = document.at("/paths/~1api~1v1~1admin~1curricula/post/responses/201/content/application~1json/example");
        assertEquals(0, created.path("version").asLong());
        assertEquals(1, created.path("courses").size());
        assertEquals(false, created.path("published").asBoolean());
        var updated = document.at("/paths/~1api~1v1~1admin~1curricula~1{entityId}/put/responses/200/content/application~1json/example");
        assertEquals(2, updated.path("version").asLong());
        assertEquals("2024 교과과정표 정정", updated.path("sourceTitle").asText());
        var published = document.at("/paths/~1api~1v1~1admin~1curricula~1{entityId}~1publication/put/responses/200/content/application~1json/example");
        assertEquals(1, published.path("version").asLong());
        assertEquals(1, published.path("courses").size());
        assertTrue(published.path("published").asBoolean());
    }

    @Test
    void inquiryAttachmentContractUsesMultipartUploadAndBinaryDownloads() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var document = objectMapper.readTree(actual);
        var upload = document.at("/paths/~1api~1v1~1inquiries~1{inquiryEntityId}~1attachments/post");
        assertTrue(upload.at("/security/0/bearerAuth").isArray());
        assertEquals("binary", upload.at("/requestBody/content/multipart~1form-data/schema/properties/file/format").asText());
        for (var code : new String[]{"201", "400", "401", "404", "409", "413", "415", "503"}) assertTrue(upload.path("responses").has(code));
        for (var path : new String[]{"~1api~1v1~1inquiries", "~1api~1v1~1admin~1inquiries"}) {
            var download = document.at("/paths/" + path + "~1{inquiryEntityId}~1attachments~1{attachmentEntityId}/get/responses/200/content");
            for (var type : new String[]{"image~1jpeg", "image~1png", "application~1pdf"}) assertEquals("binary", download.at("/" + type + "/schema/format").asText());
        }
        assertTrue(document.at("/paths/~1api~1v1~1admin~1inquiries~1{inquiryEntityId}~1attachments/get/responses").has("403"));
    }

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void generatedContractMatchesCheckedInContract() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Path generated = Path.of("build/openapi/openapi.json");
		Files.createDirectories(generated.getParent());
		Files.writeString(generated, actual);
		Path expected = Path.of("docs/api/openapi.json");
		assertTrue(Files.exists(expected), "copy build/openapi/openapi.json to docs/api/openapi.json");
		assertEquals(objectMapper.readTree(Files.readString(expected)), objectMapper.readTree(actual));
	}

	@Test
	void swaggerUiIsAvailable() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/html"));
	}

	@Test
	void simulationDocumentsJsonAuthenticationAndStableFailures() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var operation = objectMapper.readTree(actual).at("/paths/~1api~1v1~1grades~1simulation/post");
		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertEquals("#/components/schemas/GradeSimulationRequest",
			operation.at("/requestBody/content/application~1json/schema/$ref").asText());
		assertEquals("#/components/schemas/GradeSimulationResponse",
			operation.at("/responses/200/content/application~1json/schema/$ref").asText());
		assertTrue(operation.at("/responses/400").isObject());
		assertTrue(operation.at("/responses/401").isObject());
		assertTrue(operation.at("/responses/404").isObject());
	}

	@Test
	void studentInquiryEndpointsAreDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var collectionPost = document.at("/paths/~1api~1v1~1inquiries/post");
		var collectionGet = document.at("/paths/~1api~1v1~1inquiries/get");
		var itemGet = document.at("/paths/~1api~1v1~1inquiries~1{inquiryEntityId}/get");
		var itemPut = document.at("/paths/~1api~1v1~1inquiries~1{inquiryEntityId}/put");
		var itemDelete = document.at("/paths/~1api~1v1~1inquiries~1{inquiryEntityId}/delete");

		assertTrue(collectionPost.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/InquiryCreateRequest",
			collectionPost.at("/requestBody/content/application~1json/schema/$ref").asText()
		);
		assertEquals(
			"#/components/schemas/InquiryResponse",
			collectionPost.at("/responses/201/content/application~1json/schema/$ref").asText()
		);
		assertTrue(collectionGet.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"array",
			collectionGet.at("/responses/200/content/application~1json/schema/type").asText()
		);
		assertTrue(itemGet.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/InquiryResponse",
			itemGet.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(itemPut.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/InquiryCreateRequest",
			itemPut.at("/requestBody/content/application~1json/schema/$ref").asText()
		);
		assertTrue(itemPut.at("/responses/409").isObject());
		assertTrue(itemDelete.at("/security/0/bearerAuth").isArray());
		assertTrue(itemDelete.at("/responses/204").isObject());
		assertTrue(document.at("/components/schemas/InquiryResponse/properties/status/enum").toString().contains("ANSWERED"));
	}

	@Test
	void adminInquiryEndpointsAreDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var collectionGet = document.at("/paths/~1api~1v1~1admin~1inquiries/get");
		var itemGet = document.at("/paths/~1api~1v1~1admin~1inquiries~1{inquiryEntityId}/get");
		var answerPut = document.at("/paths/~1api~1v1~1admin~1inquiries~1{inquiryEntityId}~1answer/put");

		assertTrue(collectionGet.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"array",
			collectionGet.at("/responses/200/content/application~1json/schema/type").asText()
		);
		assertTrue(itemGet.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/AdminInquiryResponse",
			itemGet.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(answerPut.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/InquiryAnswerRequest",
			answerPut.at("/requestBody/content/application~1json/schema/$ref").asText()
		);
		assertEquals(
			"#/components/schemas/AdminInquiryResponse",
			answerPut.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(document.at("/components/schemas/AdminInquiryResponse/properties/deletedAt").isObject());
		assertTrue(document.at("/components/schemas/AdminInquiryResponse/properties/assignedAdmin").isObject());
		var assigneePut = document.at("/paths/~1api~1v1~1admin~1inquiries~1{inquiryEntityId}~1assignee/put");
		assertTrue(assigneePut.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/InquiryAssigneeRequest",
			assigneePut.at("/requestBody/content/application~1json/schema/$ref").asText()
		);
		assertTrue(assigneePut.at("/responses/400").isObject());
		var adminsGet = document.at("/paths/~1api~1v1~1admin~1admins/get");
		assertTrue(adminsGet.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"array",
			adminsGet.at("/responses/200/content/application~1json/schema/type").asText()
		);
	}

	@Test
	void courseRecommendationEndpointIsDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var operation = document.at("/paths/~1api~1v1~1course-recommendations/get");

		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/CourseRecommendationResponse",
			operation.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(operation.at("/responses/400").isObject());
		assertTrue(operation.at("/responses/404").isObject());
		assertTrue(document.at("/components/schemas/RecommendedCourseResponse/properties/reasons").isObject());
	}

	@Test
	void adminIdentityEndpointIsDocumentedForAuthenticatedAdministrators() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var operation = objectMapper.readTree(actual)
			.at("/paths/~1api~1v1~1admin~1me/get");

		assertTrue(operation.isObject(), "GET /api/v1/admin/me must be documented");
		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertTrue(operation.at("/responses/200").isObject());
		assertTrue(operation.at("/responses/403").isObject());
		assertEquals(
			"#/components/schemas/AdminIdentityResponse",
			operation.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertEquals(
			"#/components/schemas/ApiErrorResponse",
			operation.at("/responses/403/content/application~1json/schema/$ref").asText()
		);
	}

	@Test
	void courseDepartmentIsDocumentedAsNullable() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var departmentSchema = objectMapper.readTree(actual)
			.at("/components/schemas/CourseResponse/properties/department");

		assertEquals("null", departmentSchema.path("type").asText());
		assertEquals(
			"#/components/schemas/DepartmentResponse",
			departmentSchema.path("$ref").asText()
		);
	}

	@Test
	void gradeNullableFieldsAreDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var schemas = objectMapper.readTree(actual).at("/components/schemas");

		assertEquals(
			"null",
			schemas.at("/GradeCourseResponse/properties/department/type").asText()
		);
		assertEquals(
			objectMapper.readTree("[\"string\", \"null\"]"),
			schemas.at("/GradeResponse/properties/gradeCode/type")
		);
		assertEquals(
			objectMapper.readTree("[\"number\", \"null\"]"),
			schemas.at("/GradeResponse/properties/gradePoint/type")
		);
		assertEquals(
			objectMapper.readTree("[\"string\", \"null\"]"),
			schemas.at("/GradeRegistrationRequest/properties/replacedGradeEntityId/type")
		);
		assertEquals(
			objectMapper.readTree("[\"string\", \"null\"]"),
			schemas.at("/GradeResponse/properties/replacedGradeEntityId/type")
		);
	}

	@Test
	void gradeSummaryEndpointIsDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var summaryOperation = objectMapper.readTree(actual)
			.at("/paths/~1api~1v1~1grades~1summary/get");

		assertEquals(
			"#/components/schemas/GradeSummaryResponse",
			summaryOperation
				.at("/responses/200/content/application~1json/schema/$ref")
				.asText()
		);
		assertTrue(summaryOperation.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"array",
			objectMapper.readTree(actual)
				.at("/components/schemas/GradeSummaryResponse/properties/categories/type")
				.asText()
		);
	}

	@Test
	void graduationRequirementEndpointIsDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var operation = objectMapper.readTree(actual)
			.at("/paths/~1api~1v1~1graduation-requirements/get");

		assertEquals(
			"array",
			operation.at("/responses/200/content/application~1json/schema/type").asText()
		);
		assertEquals(
			"#/components/schemas/GraduationRequirementResponse",
			operation
				.at("/responses/200/content/application~1json/schema/items/$ref")
				.asText()
		);
		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertTrue(operation.at("/responses/404").isObject());
		var properties = objectMapper.readTree(actual)
			.at("/components/schemas/GraduationRequirementResponse/properties");
		assertTrue(properties.has("minDepartmentCredit"));
		assertTrue(properties.has("minGeneralCredit"));
		assertTrue(!properties.has("minMajorCredit"));
		assertTrue(!properties.has("minDoubleMajorCredit"));
		assertTrue(properties.has("sourceTitle"));
		assertTrue(properties.has("sourceUrl"));
	}

	@Test
	void graduationRequirementManagementEndpointsAreDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var collection = document.at(
			"/paths/~1api~1v1~1admin~1graduation-requirements/post"
		);
		var list = document.at(
			"/paths/~1api~1v1~1admin~1graduation-requirements/get"
		);
		var item = document.at(
			"/paths/~1api~1v1~1admin~1graduation-requirements~1{requirementEntityId}/put"
		);

		assertTrue(collection.at("/security/0/bearerAuth").isArray());
		assertTrue(collection.at("/responses/201").isObject());
		assertTrue(collection.at("/responses/403").isObject());
		assertTrue(list.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"array",
			list.at("/responses/200/content/application~1json/schema/type").asText()
		);
		assertTrue(item.at("/security/0/bearerAuth").isArray());
		assertTrue(item.at("/responses/200").isObject());
		assertTrue(item.at("/responses/403").isObject());
	}

	@Test
	void scholarshipEndpointsAreDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var eligibility = document.at("/paths/~1api~1v1~1scholarship-eligibility/get");
		var collection = document.at("/paths/~1api~1v1~1admin~1scholarship-criteria");
		var item = document.at("/paths/~1api~1v1~1admin~1scholarship-criteria~1{criteriaEntityId}");

		assertTrue(eligibility.at("/security/0/bearerAuth").isArray());
		assertEquals(
			"#/components/schemas/ScholarshipEligibilityResponse",
			eligibility.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(eligibility.at("/responses/404").isObject());
		assertTrue(collection.at("/get/responses/403").isObject());
		assertTrue(collection.at("/post/responses/201").isObject());
		assertTrue(collection.at("/post/responses/409").isObject());
		assertTrue(item.at("/put/responses/404").isObject());
		assertTrue(item.at("/delete/responses/204").isObject());
		assertTrue(item.at("/delete/security/0/bearerAuth").isArray());
		var result = document.at("/components/schemas/ScholarshipResultResponse/properties");
		assertTrue(result.has("gpaShortfall"));
		assertTrue(result.has("creditShortfall"));
		assertTrue(result.has("excessWarnings"));
	}

	@Test
	void graduationProgressEndpointIsDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var document = objectMapper.readTree(actual);
		var operation = document.at("/paths/~1api~1v1~1graduation-progress/get");

		assertEquals(
			"array",
			operation.at("/responses/200/content/application~1json/schema/type").asText()
		);
		assertEquals(
			"#/components/schemas/GraduationProgressResponse",
			operation
				.at("/responses/200/content/application~1json/schema/items/$ref")
				.asText()
		);
		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertTrue(operation.at("/responses/401").isObject());
		assertTrue(operation.at("/responses/404").isObject());
		assertTrue(document
			.at("/components/schemas/CreditProgressResponse/properties/remaining")
			.isObject());
		assertTrue(document
			.at("/components/schemas/CreditProgressResponse/properties/met")
			.isObject());
	}

	@Test
	void graduationPolicyImpactEndpointIsDocumented() throws Exception {
		String actual = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var document = objectMapper.readTree(actual);
		var operation = document.at(
			"/paths/~1api~1v1~1admin~1graduation-requirements~1{requirementEntityId}~1impact/get"
		);
		assertTrue(operation.at("/security/0/bearerAuth").isArray());
		assertTrue(operation.at("/responses/403").isObject());
		assertTrue(operation.at("/responses/404").isObject());
		assertEquals(
			"#/components/schemas/GraduationRequirementImpactResponse",
			operation.at("/responses/200/content/application~1json/schema/$ref").asText()
		);
		assertTrue(document.at(
			"/components/schemas/GraduationRequirementImpactResponse/properties/affectedStudentCount"
		).isObject());
	}

}
