package com.activitynotifier;

import com.activitynotifier.damage.DamageAlertTracker;
import com.activitynotifier.damage.DamageSettings;
import com.activitynotifier.detection.Activity;
import com.activitynotifier.detection.ActivityTracker;
import com.activitynotifier.detection.AnimationCatalog;
import com.activitynotifier.detection.CustomAnimations;
import com.activitynotifier.detection.Detector;
import com.activitynotifier.detection.WeaponSpeed;
import com.activitynotifier.detection.XpGainTracker;
import com.activitynotifier.resources.ResourceAlertTracker;
import com.activitynotifier.resources.ResourceType;
import com.activitynotifier.signals.SignalDispatcher;
import com.activitynotifier.ui.ActivityNotifierPanel;
import com.activitynotifier.warnings.LogoutWarningTracker;
import com.google.inject.Provides;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Activity Notifier",
	description = "Signals when you stop and resume activity, in a format the Watchdog plugin can react to",
	tags = {"idle", "afk", "activity", "notification", "notifier", "watchdog", "skilling", "damage", "hit", "animation",
		"inactive", "inactivity", "reminder", "alert", "warning", "low", "hp", "hitpoints", "prayer", "run", "energy", "spec",
		"logout", "combat"}
)
public class ActivityNotifierPlugin extends Plugin
{
	static final String CLEARED_PLUGIN_STOPPED = "Plugin stopped";
	static final String CLEARED_PAUSED = "Detection paused";

	private static final String ATTACK_ACTION = "Attack";
	private static final String SHOW_SIDE_PANEL_KEY = "showSidePanel";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ActivityNotifierConfig config;

	@Inject
	private SignalDispatcher dispatcher;

	@Inject
	private KeyManager keyManager;

	@Inject
	private WeaponSpeed weaponSpeed;

	@Inject
	private ClientToolbar clientToolbar;

	private final Clock clock = Clock.systemUTC();
	private final XpGainTracker xpGainTracker = new XpGainTracker();
	private final LogoutWarningTracker logoutWarnings = new LogoutWarningTracker();

	/**
	 * Set when a new game session starts (login, hop, reconnect), so the next LOGGED_IN starts the
	 * six-hour timer. Matches core Idle Notifier. Client thread only.
	 */
	private boolean sessionStarting;

	private final HotkeyListener pauseHotkeyListener = new HotkeyListener(() -> config.pauseHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			// Fired on the AWT thread; tracker state belongs to the client thread
			clientThread.invoke(ActivityNotifierPlugin.this::togglePause);
		}
	};

	private ActivityTracker activityTracker;

	/**
	 * True once the player is in the world after login/hop/loading (FR-008). Client thread only.
	 */
	private boolean ready;
	private boolean paused;

	/**
	 * The local player's location on the previous tick, for the movement detector. Client thread only.
	 */
	private WorldPoint lastLocation;

	private Map<ResourceType, ResourceAlertTracker> resourceTrackers;
	private DamageAlertTracker damageTracker;

	/**
	 * The player's "Custom animation IDs", parsed when the setting's text changes. Client thread only.
	 */
	private CustomAnimations customAnimations;

	/**
	 * The last animation sent to the side panel, so it is only updated when a new one starts. Client thread
	 * only. Not reset on logout or hop, so the panel keeps showing it.
	 */
	private int lastAnimationShown;

	private ActivityNotifierPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = new ActivityNotifierPanel(configManager, config);
		navButton = NavigationButton.builder()
			.tooltip("Activity Notifier")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(10)
			.panel(panel)
			.build();
		if (config.showSidePanel())
		{
			clientToolbar.addNavigation(navButton);
		}

		activityTracker = new ActivityTracker(this::effectiveDelayMs, d -> d.isEnabled(configManager), this::reminderInterval);
		resourceTrackers = new EnumMap<>(ResourceType.class);
		for (ResourceType type : ResourceType.values())
		{
			resourceTrackers.put(type, new ResourceAlertTracker(type));
		}
		damageTracker = new DamageAlertTracker();
		customAnimations = new CustomAnimations();
		lastAnimationShown = -1;
		weaponSpeed.reset();
		xpGainTracker.reset();
		logoutWarnings.reset();
		sessionStarting = false;
		ready = false;
		paused = false;
		lastLocation = null;
		keyManager.registerKeyListener(pauseHotkeyListener);
		log.debug("Activity Notifier started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(pauseHotkeyListener);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;

		// Clear any outstanding Inactive or resource alert so dependent Watchdog effects are removed (FR-012)
		final ActivityTracker tracker = activityTracker;
		final Map<ResourceType, ResourceAlertTracker> resources = resourceTrackers;
		clientThread.invoke(() ->
		{
			tracker.clear(CLEARED_PLUGIN_STOPPED).ifPresent(dispatcher::dispatch);
			clearResources(resources, CLEARED_PLUGIN_STOPPED);
		});
		log.debug("Activity Notifier stopped");
	}

	@Provides
	ActivityNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ActivityNotifierConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ActivityNotifierConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (SHOW_SIDE_PANEL_KEY.equals(event.getKey()))
		{
			// applies immediately, without restarting the plugin (FR-029)
			if (config.showSidePanel())
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
			return;
		}

		refreshPanel();
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		// every setting may have changed (FR-022)
		refreshPanel();
	}

	private void refreshPanel()
	{
		final ActivityNotifierPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		trackSession(event.getGameState());

		switch (event.getGameState())
		{
			case LOGGED_IN:
				// becomes ready on the next game tick, once the player is in the world
				break;
			case LOADING:
				// keep state across loading screens so an outstanding Inactive is never dropped
				ready = false;
				lastLocation = null;
				activityTracker.suspend(clock.instant());
				break;
			default:
				// logout, hop, connection lost: discard state silently
				ready = false;
				lastLocation = null;
				activityTracker.reset();
				xpGainTracker.reset();
				resourceTrackers.values().forEach(ResourceAlertTracker::reset);
				damageTracker.discardPending();
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Instant now = clock.instant();
		if (!ready)
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			ready = true;
			// covers starting the plugin mid-session, when no ItemContainerChanged has been seen
			weaponSpeed.update(client.getItemContainer(InventoryID.WORN));
			if (!xpGainTracker.isSeeded())
			{
				seedXp();
			}
			activityTracker.resume(now);
		}

		// warnings matter most while AFK, so they are not affected by the pause hotkey
		checkLogoutWarnings(now);

		if (paused)
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local != null)
		{
			// Long animations and interactions don't re-fire events, so re-observe them each tick
			int animation = local.getAnimation();
			observeAnimation(animation);
			Actor target = local.getInteracting();
			if (target != null)
			{
				observeInteraction(target);
			}
			observeMovement(local.getWorldLocation(), animation);
		}

		activityTracker.evaluate(now).ifPresent(dispatcher::dispatch);

		for (ResourceType type : ResourceType.values())
		{
			sampleResource(type);
		}

		flushDamage(now);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		int animation = event.getActor().getAnimation();
		// shown even while paused or with detectors off, so players can look up IDs (003 FR-013)
		showLastAnimation(animation);
		observeAnimation(animation);
	}

	/**
	 * Sends a newly started animation to the side panel, on the EDT. No animation (-1) never replaces the
	 * one shown.
	 */
	private void showLastAnimation(int animation)
	{
		if (animation == -1 || animation == lastAnimationShown)
		{
			return;
		}
		lastAnimationShown = animation;

		final ActivityNotifierPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setLastAnimation(animation));
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer() || event.getTarget() == null)
		{
			return;
		}
		observeInteraction(event.getTarget());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		xpGainTracker.onXp(event.getSkill(), event.getXp(), config.xpThreshold())
			.ifPresent(skill -> observe(Detector.XP_GAIN, skill.getName()));

		// sample HP and prayer as soon as they change, not only on the next tick (research R8)
		if (ready && !paused)
		{
			if (event.getSkill() == Skill.HITPOINTS)
			{
				sampleResource(ResourceType.HITPOINTS);
			}
			else if (event.getSkill() == Skill.PRAYER)
			{
				sampleResource(ResourceType.PRAYER);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();
		if (event.getActor() == client.getLocalPlayer())
		{
			// damage taken, which is not activity. Sent on the next tick, once hitpoints include it
			if (config.alertDamage() && ready && !paused)
			{
				damageTracker.record(hitsplat.getHitsplatType(), hitsplat.getAmount());
			}
			return;
		}

		if (hitsplat.isMine())
		{
			observe(Detector.COMBAT, Detector.COMBAT.getDisplayName());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			weaponSpeed.update(event.getItemContainer());
		}
	}

	private void trackSession(GameState state)
	{
		switch (state)
		{
			case LOGIN_SCREEN:
				sessionStarting = false;
				logoutWarnings.reset();
				break;
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				// the game's six-hour timer restarts with each new world session, as core assumes
				sessionStarting = true;
				break;
			case LOGGED_IN:
				if (sessionStarting)
				{
					sessionStarting = false;
					logoutWarnings.onLogin(clock.instant());
				}
				break;
			default:
				break;
		}
	}

	private void checkLogoutWarnings(Instant now)
	{
		if (config.warnIdleLogout())
		{
			int idleTicks = Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks());
			logoutWarnings.checkIdle(idleTicks, client.getIdleTimeout()).ifPresent(dispatcher::dispatch);
		}
		if (config.warnSixHour())
		{
			logoutWarnings.checkSession(now).ifPresent(dispatcher::dispatch);
		}
	}

	private void sampleResource(ResourceType type)
	{
		ResourceAlertTracker tracker = resourceTrackers.get(type);
		int threshold = type.threshold(configManager);
		if (!type.isEnabled(configManager) || !resourceApplies(type, threshold))
		{
			// an alert turned off (or no longer applicable) while triggered sends its recovered signal
			tracker.clear(ResourceAlertTracker.CLEARED_ALERT_OFF).ifPresent(dispatcher::dispatch);
			return;
		}
		tracker.sample(readResource(type), threshold).ifPresent(dispatcher::dispatch);
	}

	/**
	 * Core Idle Notifier's guard: a level at or below the threshold would leave the alert permanently on.
	 */
	private boolean resourceApplies(ResourceType type, int threshold)
	{
		switch (type)
		{
			case HITPOINTS:
				return client.getRealSkillLevel(Skill.HITPOINTS) > threshold;
			case PRAYER:
				return client.getRealSkillLevel(Skill.PRAYER) > threshold;
			default:
				return true;
		}
	}

	/**
	 * Reads a resource's current value in its unit (research R8).
	 */
	private int readResource(ResourceType type)
	{
		switch (type)
		{
			case HITPOINTS:
				// Nightmare Zone absorption counts as hitpoints, as in core
				return client.getBoostedSkillLevel(Skill.HITPOINTS) + client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
			case PRAYER:
				return client.getBoostedSkillLevel(Skill.PRAYER);
			case RUN_ENERGY_LOW:
			case RUN_ENERGY_HIGH:
				// energy is in hundredths of a percent
				return client.getEnergy() / 100;
			case OXYGEN:
				// tenths of a percent
				return client.getVarbitValue(VarbitID.FOSSIL_UNDERWATER_OXYGEN) / 10;
			case SPECIAL_ATTACK:
				// tenths of a percent
				return client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
			default:
				throw new IllegalArgumentException(type.toString());
		}
	}

	private void flushDamage(Instant now)
	{
		if (!config.alertDamage())
		{
			// a hit recorded just before the alert was turned off is never sent
			damageTracker.discardPending();
			return;
		}
		damageTracker.flush(damageSettings(), client.getBoostedSkillLevel(Skill.HITPOINTS), now)
			.forEach(dispatcher::dispatch);
	}

	private DamageSettings damageSettings()
	{
		return new DamageSettings(config.damageThreshold(), config.damageOverTime(),
			Duration.ofSeconds(Math.max(0, config.damageCooldown())));
	}

	private void clearResources(Map<ResourceType, ResourceAlertTracker> trackers, String detail)
	{
		for (ResourceAlertTracker tracker : trackers.values())
		{
			tracker.clear(detail).ifPresent(dispatcher::dispatch);
		}
	}

	private void seedXp()
	{
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			xp.put(skill, client.getSkillExperience(skill));
		}
		xpGainTracker.seed(xp);
	}

	private void observeAnimation(int animation)
	{
		if (AnimationCatalog.isHelmAnimation(animation))
		{
			// steering a ship counts only towards Sailing (analysis decision A1)
			observe(Detector.SAILING, Detector.SAILING.getDisplayName());
			return;
		}

		String name = AnimationCatalog.activityFor(animation);
		if (name == null)
		{
			// checked last, so a listed built-in or helm animation keeps its built-in meaning (003 FR-004)
			if (customAnimations.ids(config.customAnimationIds()).contains(animation))
			{
				observe(Detector.ANIMATION, Detector.ANIMATION.getDisplayName());
			}
			return;
		}

		// specific first, so a resume from Inactive is named after it (FR-010a)
		Detector specific = Detector.forCatalogActivity(name);
		if (specific != null)
		{
			observe(specific, specific.getDisplayName());
		}
		observe(Detector.ANIMATION, name);
	}

	private void observeInteraction(Actor target)
	{
		if (isAttackable(target))
		{
			observe(Detector.COMBAT, Detector.COMBAT.getDisplayName());
			observe(Detector.INTERACTION, Detector.COMBAT.getDisplayName());
		}
		else
		{
			observe(Detector.INTERACTION, Detector.INTERACTION.getDisplayName());
		}
	}

	private void observeMovement(WorldPoint location, int animation)
	{
		WorldPoint previous = lastLocation;
		lastLocation = location;
		// ship-helm animations are not movement (analysis decision A1)
		if (previous != null && !previous.equals(location) && !AnimationCatalog.isHelmAnimation(animation))
		{
			observe(Detector.MOVEMENT, Detector.MOVEMENT.getDisplayName());
		}
	}

	private void observe(Detector detector, String activityName)
	{
		if (!ready || paused)
		{
			return;
		}
		activityTracker.observe(detector, new Activity(activityName, detector), clock.instant())
			.ifPresent(dispatcher::dispatch);
	}

	private static boolean isAttackable(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}

		NPC npc = (NPC) target;
		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			composition = npc.getComposition();
		}
		if (composition == null)
		{
			return false;
		}

		for (String action : composition.getActions())
		{
			if (ATTACK_ACTION.equals(action))
			{
				return true;
			}
		}
		return false;
	}

	private void togglePause()
	{
		paused = !paused;
		lastLocation = null;
		if (paused)
		{
			activityTracker.clear(CLEARED_PAUSED).ifPresent(dispatcher::dispatch);
			clearResources(resourceTrackers, CLEARED_PAUSED);
			// damage taken is a one-off event, so there is nothing to clear, only hits not yet sent
			damageTracker.discardPending();
		}
		else
		{
			// resuming starts fresh: no signal for what happened while paused
			activityTracker.reset();
			resourceTrackers.values().forEach(ResourceAlertTracker::reset);
		}
		dispatcher.info(paused ? "Activity detection paused." : "Activity detection resumed.");
	}

	private long effectiveDelayMs(Detector detector)
	{
		return ActivityTracker.effectiveDelayMs(detector, config.baseDelay(), detector.extraDelayMs(configManager),
			weaponSpeed.getIntervalTicks());
	}

	private Duration reminderInterval()
	{
		return config.reminderEnabled() ? Duration.ofSeconds(config.reminderInterval()) : null;
	}
}
