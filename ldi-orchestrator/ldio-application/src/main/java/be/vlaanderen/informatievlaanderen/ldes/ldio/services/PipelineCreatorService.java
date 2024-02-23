package be.vlaanderen.informatievlaanderen.ldes.ldio.services;

import be.vlaanderen.informatievlaanderen.ldes.ldi.services.ComponentExecutor;
import be.vlaanderen.informatievlaanderen.ldes.ldi.types.LdiAdapter;
import be.vlaanderen.informatievlaanderen.ldes.ldi.types.LdiComponent;
import be.vlaanderen.informatievlaanderen.ldes.ldi.types.LdiOutput;
import be.vlaanderen.informatievlaanderen.ldes.ldio.components.*;
import be.vlaanderen.informatievlaanderen.ldes.ldio.config.OrchestratorConfig;
import be.vlaanderen.informatievlaanderen.ldes.ldio.config.PipelineConfig;
import be.vlaanderen.informatievlaanderen.ldes.ldio.configurator.LdioConfigurator;
import be.vlaanderen.informatievlaanderen.ldes.ldio.configurator.LdioInputConfigurator;
import be.vlaanderen.informatievlaanderen.ldes.ldio.configurator.LdioTransformerConfigurator;
import be.vlaanderen.informatievlaanderen.ldes.ldio.events.SenderCreatedEvent;
import be.vlaanderen.informatievlaanderen.ldes.ldio.exception.InvalidComponentException;
import be.vlaanderen.informatievlaanderen.ldes.ldio.exception.InvalidPipelineNameException;
import be.vlaanderen.informatievlaanderen.ldes.ldio.exception.LdiAdapterMissingException;
import be.vlaanderen.informatievlaanderen.ldes.ldio.types.LdioInput;
import be.vlaanderen.informatievlaanderen.ldes.ldio.types.LdioTransformer;
import be.vlaanderen.informatievlaanderen.ldes.ldio.valueobjects.ComponentDefinition;
import be.vlaanderen.informatievlaanderen.ldes.ldio.valueobjects.ComponentProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static be.vlaanderen.informatievlaanderen.ldes.ldio.config.OrchestratorConfig.DEBUG;
import static be.vlaanderen.informatievlaanderen.ldes.ldio.config.OrchestratorConfig.ORCHESTRATOR_NAME;
import static be.vlaanderen.informatievlaanderen.ldes.ldio.config.PipelineConfig.NAME_PATTERN;

@Service
public class PipelineCreatorService {

	private final Pattern validPipelineNamePattern = Pattern.compile(NAME_PATTERN);
	private final String orchestratorName;
	private final ConfigurableApplicationContext configContext;
	private final ApplicationEventPublisher eventPublisher;
	private final ObservationRegistry observationRegistry;
	private final DefaultListableBeanFactory beanFactory;

	public PipelineCreatorService(OrchestratorConfig orchestratorConfig, ConfigurableApplicationContext configContext, ApplicationEventPublisher eventPublisher, ObservationRegistry observationRegistry) {
		this.orchestratorName = orchestratorConfig.getName();
		this.configContext = configContext;
		this.eventPublisher = eventPublisher;
		this.observationRegistry = observationRegistry;
		this.beanFactory = (DefaultListableBeanFactory) configContext.getBeanFactory();
	}

	public void initialisePipeline(PipelineConfig config) throws InvalidComponentException, InvalidPipelineNameException, LdiAdapterMissingException {
		try {
			String pipeLineName = config.getName();
			validateName(pipeLineName);

			String inputName = config.getInput().getName();
			LdioInputConfigurator configurator = (LdioInputConfigurator) configContext.getBean(inputName);

			LdiAdapter adapter = Optional.ofNullable(config.getInput().getAdapter())
					.map(this::getLdioAdapter)
					.orElse(null);

			ComponentExecutor executor = componentExecutor(config);

			Map<String, String> inputConfig = new HashMap<>(config.getInput().getConfig().getConfig());
			inputConfig.put(ORCHESTRATOR_NAME, orchestratorName);

			Object ldiInput = configurator.configure(adapter, executor, new ComponentProperties(pipeLineName, inputName, inputConfig));

			registerBean(pipeLineName, ldiInput);
		} catch (NoSuchBeanDefinitionException e) {
			throw new InvalidComponentException(config.getName(), e.getBeanName());
		}
	}

	public void removePipeline(String pipeline) {
		DefaultListableBeanFactory beanRegistry = (DefaultListableBeanFactory) configContext.getBeanFactory();
		LdioInput ldioInput = (LdioInput) beanRegistry.getBean(pipeline);
		ldioInput.shutdown();
		beanFactory.destroyBean(pipeline);
	}

	private ComponentExecutor componentExecutor(final PipelineConfig pipelineConfig) {
		List<LdioTransformer> ldioTransformers = pipelineConfig.getTransformers()
				.stream()
				.map(this::getLdioTransformer)
				.toList();

		List<LdiOutput> ldiOutputs = pipelineConfig.getOutputs()
				.stream()
				.map(this::getLdioOutput)
				.toList();

		LdioSender ldioSender = new LdioSender(pipelineConfig.getName(), eventPublisher, ldiOutputs);

		List<LdioTransformer> processorChain = new ArrayList<>(ldioTransformers.subList(0, ldioTransformers.size()));

		processorChain.add(ldioSender);

		LdioTransformer ldioTransformerPipeline = processorChain.get(0);

		if (processorChain.size() > 1) {
			ldioTransformerPipeline = LdioTransformer.link(processorChain.get(0), processorChain);
		}

		registerBean(pipelineConfig.getName() + "-ldiSender", ldioSender);

		eventPublisher.publishEvent(new SenderCreatedEvent(pipelineConfig.getName(), ldioSender));

		return new ComponentExecutorImpl(ldioTransformerPipeline);
	}

	private LdiAdapter getLdioAdapter(ComponentDefinition componentDefinition) {
		boolean debug = componentDefinition.getConfig().getOptionalBoolean(DEBUG).orElse(false);

		LdiAdapter adapter = (LdiAdapter) getLdiComponent(componentDefinition.getName(),
				componentDefinition.getConfig());

		return debug ? new AdapterDebugger(adapter) : adapter;
	}

	private LdioTransformer getLdioTransformer(ComponentDefinition componentDefinition) {
		boolean debug = componentDefinition.getConfig().getOptionalBoolean(DEBUG).orElse(false);

		LdioTransformer ldiTransformer = ((LdioTransformerConfigurator) configContext
				.getBean(componentDefinition.getName()))
				.configure(componentDefinition.getConfig());

		return debug ? new TransformerDebugger(ldiTransformer) : ldiTransformer;
	}

	private LdiOutput getLdioOutput(ComponentDefinition componentDefinition) {
		boolean debug = componentDefinition.getConfig().getOptionalBoolean(DEBUG).orElse(false);

		LdiOutput ldiOutput = (LdiOutput) getLdiComponent(componentDefinition.getName(),
				componentDefinition.getConfig());


		return debug ?
				new LdiOutputLogger(new OutputDebugger(ldiOutput), observationRegistry) :
				new LdiOutputLogger(ldiOutput, observationRegistry);
	}

	private LdiComponent getLdiComponent(String beanName, ComponentProperties config) {
		return ((LdioConfigurator) configContext.getBean(beanName)).configure(config);
	}

	private void registerBean(String pipelineName, Object bean) {
		if (!beanFactory.containsSingleton(pipelineName)) {
			beanFactory.registerSingleton(pipelineName, bean);
		}
	}

	private void validateName(String name) {
		Matcher matcher = validPipelineNamePattern.matcher(name);

		if (!matcher.matches()) {
			throw new InvalidPipelineNameException(name);
		}
	}
}