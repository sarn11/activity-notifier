package com.activitynotifier;

import com.activitynotifier.detection.Activity;
import com.activitynotifier.detection.ActivityTracker;
import com.activitynotifier.detection.AnimationCatalog;
import com.activitynotifier.detection.Detector;
import com.activitynotifier.signals.SignalDispatcher;
import com.google.inject.Provides;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Activity Notifier",
	description = "Signals when you stop and resume activity, in a format the Watchdog plugin can react to",
	tags = {"idle", "afk", "activity", "notification", "notifier", "watchdog", "skilling"}
)
public class ActivityNotifierPlugin extends Plugin
{
	static final String CLEARED_PLUGIN_STOPPED = "Plugin stopped";
	static final String CLEARED_PAUSED = "Detection paused";

	private static final String COMBAT = "Combat";
	private static final String ATTACK_ACTION = "Attack";

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

	private final Clock clock = Clock.systemUTC();

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

	@Override
	protected void startUp() throws Exception
	{
		activityTracker = new ActivityTracker(this::effectiveDelayMs, d -> d.isEnabled(configManager), this::reminderInterval);
		ready = false;
		paused = false;
		keyManager.registerKeyListener(pauseHotkeyListener);
		log.debug("Activity Notifier started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(pauseHotkeyListener);

		// Clear any outstanding Inactive so dependent Watchdog effects are removed (FR-012)
		final ActivityTracker tracker = activityTracker;
		clientThread.invoke(() -> tracker.clear(CLEARED_PLUGIN_STOPPED).ifPresent(dispatcher::dispatch));
		log.debug("Activity Notifier stopped");
	}

	@Provides
	ActivityNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ActivityNotifierConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				// becomes ready on the next game tick, once the player is in the world
				break;
			case LOADING:
				// keep state across loading screens so an outstanding Inactive is never dropped
				ready = false;
				activityTracker.suspend(clock.instant());
				break;
			default:
				// logout, hop, connection lost: discard state silently
				ready = false;
				activityTracker.reset();
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
			activityTracker.resume(now);
		}

		if (paused)
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local != null)
		{
			// Long animations and interactions don't re-fire events, so re-observe them each tick
			observeAnimation(local.getAnimation());
			Actor target = local.getInteracting();
			if (target != null)
			{
				observeInteraction(target);
			}
		}

		activityTracker.evaluate(now).ifPresent(dispatcher::dispatch);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		observeAnimation(event.getActor().getAnimation());
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

	private void observeAnimation(int animation)
	{
		String name = AnimationCatalog.activityFor(animation);
		if (name != null)
		{
			observe(Detector.ANIMATION, name);
		}
	}

	private void observeInteraction(Actor target)
	{
		observe(Detector.INTERACTION, isAttackable(target) ? COMBAT : Detector.INTERACTION.getDisplayName());
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
		if (paused)
		{
			activityTracker.clear(CLEARED_PAUSED).ifPresent(dispatcher::dispatch);
		}
		else
		{
			// resuming starts fresh: no signal for what happened while paused
			activityTracker.reset();
		}
		dispatcher.info(paused ? "Activity detection paused." : "Activity detection resumed.");
	}

	private long effectiveDelayMs(Detector detector)
	{
		return (long) Math.max(0, config.baseDelay()) + detector.extraDelayMs(configManager);
	}

	private Duration reminderInterval()
	{
		return config.reminderEnabled() ? Duration.ofSeconds(config.reminderInterval()) : null;
	}
}
