package be.vlaanderen.informatievlaanderen.ldes.ldio;

import be.vlaanderen.informatievlaanderen.ldes.ldi.requestexecutor.executor.edc.services.TokenService;
import be.vlaanderen.informatievlaanderen.ldes.ldi.requestexecutor.executor.edc.services.TransferService;
import be.vlaanderen.informatievlaanderen.ldes.ldi.requestexecutor.valueobjects.Response;
import be.vlaanderen.informatievlaanderen.ldes.ldi.services.ComponentExecutor;
import be.vlaanderen.informatievlaanderen.ldes.ldio.events.PipelineCreatedEvent;
import be.vlaanderen.informatievlaanderen.ldes.ldio.events.PipelineDeletedEvent;
import be.vlaanderen.informatievlaanderen.ldes.ldio.statusmanagement.PipelineStatusManager;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LdioLdesClientConnectorApiController.class)
@ComponentScan(basePackages = "be.vlaanderen.informatievlaanderen.ldes.ldio.collection")
@AutoConfigureMockMvc
class LdioLdesClientConnectorTest {

	@Autowired
	ApplicationEventPublisher eventPublisher;
	@Autowired
	private MockMvc mockMvc;
	private final String endpoint = "endpoint";
	private TransferService transferService;
	private TokenService tokenService;

	@BeforeEach
	void setup() {
		transferService = mock(TransferService.class);
		tokenService = mock(TokenService.class);
		final LdioLdesClientConnectorApi ldioLdesClientConnectorApi = new LdioLdesClientConnectorApi(
				mock(ComponentExecutor.class),
				endpoint,
				null,
				mock(MemberSupplier.class),
				eventPublisher,
				transferService,
				tokenService);
		eventPublisher.publishEvent(new PipelineCreatedEvent(PipelineStatusManager.initialize(endpoint, ldioLdesClientConnectorApi, List.of())));
	}

	@Test
	void testTokenEndpoint() throws Exception {
		String content = "token";

		mockMvc.perform(post("/%s/token".formatted(endpoint)).content(content)).andExpect(status().isAccepted());

		verify(tokenService).updateToken(content);
		verify(transferService, never()).startTransfer(any());
	}

	@Test
	void testTransferEndpoint() throws Exception {
		String content = "transfer";
		String transferResult = "transfer-result";
		when(transferService.startTransfer(content)).thenReturn(new Response(null, List.of(), 200, "transfer-result"));

		mockMvc.perform(post("/%s/transfer".formatted(endpoint)).content(content))
				.andExpect(status().isAccepted())
				.andExpect(content().string(transferResult));

		verify(transferService).startTransfer(content);
		verify(tokenService, never()).updateToken(any());
	}

	@Test
	void given_ExistingPipeline_when_DeletePipeline_then_ShutdownTokenService() {
		eventPublisher.publishEvent(new PipelineDeletedEvent(endpoint));

		verify(tokenService).shutdown();
	}

	@Test
	void given_NonExistingPipeline_when_DeletePipeline_then_DoNothing() {
		eventPublisher.publishEvent(new PipelineDeletedEvent("non-existing-pipeline"));

		verifyNoInteractions(tokenService);
	}
}
