package com.violet.utils;

import java.lang.annotation.Annotation;

import jodd.db.DbManager;
import jodd.db.DbSessionProvider;
import jodd.db.connection.ConnectionProvider;
import jodd.db.jtx.DbJtxSessionProvider;
import jodd.db.jtx.DbJtxTransactionManager;
import jodd.db.oom.DbOomManager;
import jodd.db.oom.config.AutomagicDbOomConfigurator;
import jodd.db.pool.CoreConnectionPool;
import jodd.jtx.JtxTransactionManager;
import jodd.jtx.meta.Transaction;
import jodd.jtx.proxy.AnnotationTxAdvice;
import jodd.jtx.proxy.AnnotationTxAdviceManager;
import jodd.jtx.proxy.AnnotationTxAdviceSupport;
import jodd.log.Logger;
import jodd.log.LoggerFactory;
import jodd.log.impl.Slf4jLoggerFactory;
import jodd.petite.PetiteContainer;
import jodd.petite.config.AutomagicPetiteConfigurator;
import jodd.petite.proxetta.ProxettaAwarePetiteContainer;
import jodd.props.Props;
import jodd.props.PropsUtil;
import jodd.proxetta.MethodInfo;
import jodd.proxetta.ProxyAspect;
import jodd.proxetta.impl.ProxyProxetta;
import jodd.proxetta.pointcuts.MethodAnnotationPointcut;

public class DefaultAppCore {
	public static final String PETITE_CORE = "core";
	public static final String PETITE_PROPS = "props";
	public static final String PETITE_DBPOOL = "dbpool";
	public static final String PETITE_DB = "db";
	public static final String PETITE_DBOOM = "dboom";

	public void init() {
		initLogger();

		log.info("===========MyAppCore starting===========");

		initCore();
		initProps();
	}

	public void start() {
		init();

		try {
			startProxetta();
			startPetite();
			startDb();

			log.info("===========MyAppCore started===========");
		} catch (RuntimeException rex) {
			if (log != null) {
				log.error(rex.toString(), rex);
			} else {
				System.out.println(rex.toString());
				rex.printStackTrace();
			}
			try {
				stop();
			} catch (Exception ignore) {
			}
			throw rex;
		}
	}

	public void stop() {
		if (log != null) {
			log.info("shutting down...");
		}

		stopDb();
		stopPetite();

		if (log != null) {
			log.info("app stopped");
		}
	}

	// ----------------------------------------------------------------
	// init

	protected String dbPropsNamePattern;
	protected boolean useDatabase;
	protected Class<? extends ConnectionProvider> connectionProviderClass;
	protected Class<? extends Annotation>[] jtxAnnotations;
	protected String jtxScopePattern;

	@SuppressWarnings("unchecked")
	protected void initCore() {
		dbPropsNamePattern = "/db*.prop*";
		useDatabase = true;
		connectionProviderClass = CoreConnectionPool.class;
		jtxAnnotations = new Class[] { Transaction.class };
		jtxScopePattern = "$class";
	}

	// ----------------------------------------------------------------
	// logger

	protected static Logger log;

	protected void initLogger() {
		LoggerFactory.setLoggerFactory(new Slf4jLoggerFactory());

		log = LoggerFactory.getLogger(DefaultAppCore.class);
	}

	// ----------------------------------------------------------------
	// props

	protected Props appProps;

	protected void initProps() {
		appProps = createProps();

		PropsUtil.loadFromClasspath(appProps, dbPropsNamePattern);
	}

	private Props createProps() {
		Props props = new Props();
		props.setSkipEmptyProps(true);
		props.setIgnoreMissingMacros(true);
		return props;
	}

	// ----------------------------------------------------------------
	// proxetta

	protected ProxyProxetta proxetta;

	protected void startProxetta() {
		proxetta = ProxyProxetta.withAspects(createAppAspects());
	}

	private ProxyAspect[] createAppAspects() {
		return new ProxyAspect[] { createTxProxyAspects() };
	}

	private ProxyAspect createTxProxyAspects() {
		return new ProxyAspect(AnnotationTxAdvice.class, new MethodAnnotationPointcut(jtxAnnotations) {
			@Override
			public boolean apply(MethodInfo methodInfo) {
				return isPublic(methodInfo) && isTopLevelMethod(methodInfo) && super.apply(methodInfo);
			}
		});
	}

	// ----------------------------------------------------------------
	// petite

	protected PetiteContainer petite;

	public PetiteContainer getPetite() {
		return petite;
	}

	protected void startPetite() {
		petite = createPetiteContainer();

		petite.defineParameters(appProps);

		registerPetiteContainerBeans(petite);

		petite.addBean(PETITE_CORE, this);
		petite.addBean(PETITE_PROPS, appProps);
	}

	protected void stopPetite() {
		if (petite != null) {
			petite.shutdown();
		}
	}

	private PetiteContainer createPetiteContainer() {
		return new ProxettaAwarePetiteContainer(proxetta);
	}

	private void registerPetiteContainerBeans(PetiteContainer petiteContainer) {
		AutomagicPetiteConfigurator pcfg = new AutomagicPetiteConfigurator();
		pcfg.configure(petiteContainer);
	}

	// ----------------------------------------------------------------
	// datasource
	protected ConnectionProvider connectionProvider;

	protected void initConnectionProvider() {
		petite.registerPetiteBean(connectionProviderClass, PETITE_DBPOOL, null, null, false);

		connectionProvider = (ConnectionProvider) petite.getBean(PETITE_DBPOOL);

		connectionProvider.init();
	}

	// ----------------------------------------------------------------
	// Jtx
	protected JtxTransactionManager jtxManager;

	protected void initJtxTransactionManager() {
		jtxManager = createJtxTransactionManager(connectionProvider);
		jtxManager.setValidateExistingTransaction(true);

		AnnotationTxAdviceManager annTxAdviceManager = new AnnotationTxAdviceManager(jtxManager, jtxScopePattern);
		annTxAdviceManager.registerAnnotations(jtxAnnotations);
		AnnotationTxAdviceSupport.manager = annTxAdviceManager;
	}

	private JtxTransactionManager createJtxTransactionManager(ConnectionProvider connectionProvider) {
		return new DbJtxTransactionManager(connectionProvider);
	}

	// ----------------------------------------------------------------
	// database
	protected void startDb() {
		if (!useDatabase) {
			log.info("database is not used");
			return;
		}

		initConnectionProvider();

		initJtxTransactionManager();

		DbSessionProvider sessionProvider = new DbJtxSessionProvider(jtxManager);

		DbManager dbManager = DbManager.getInstance();
		dbManager.setConnectionProvider(connectionProvider);
		dbManager.setSessionProvider(sessionProvider);
		petite.addBean(PETITE_DB, dbManager);

		DbOomManager dbOomManager = DbOomManager.getInstance();
		petite.addBean(PETITE_DBOOM, dbOomManager);

		registerDbEntities(dbOomManager);
	}

	private void registerDbEntities(DbOomManager dbOomManager) {
		AutomagicDbOomConfigurator dbcfg = new AutomagicDbOomConfigurator();
		dbcfg.configure(dbOomManager);
	}

	protected void stopDb() {
		if (!useDatabase) {
			return;
		}

		if (log != null) {
			log.info("database shutdown");
		}

		if (jtxManager != null) {
			jtxManager.close();
		}

		if (connectionProvider != null) {
			connectionProvider.close();
		}
	}
}
