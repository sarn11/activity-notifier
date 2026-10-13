package com.activitynotifier.signals;

import com.activitynotifier.ActivityNotifierConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/**
 * Delivers signals to the enabled channels: a filtered chat game message and/or a RuneLite notification.
 * Watchdog matches the former with a Game Message alert (message type Spam or Any) and the latter with a
 * Notification Fired alert.
 */
@Slf4j
@Singleton
public class SignalDispatcher
{
	private final ActivityNotifierConfig config;
	private final ChatMessageManager chatMessageManager;
	private final Notifier notifier;

	@Inject
	SignalDispatcher(ActivityNotifierConfig config, ChatMessageManager chatMessageManager, Notifier notifier)
	{
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.notifier = notifier;
	}

	public void dispatch(Signal signal)
	{
		String text = SignalFormatter.format(signal, templateFor(signal.getType()));
		log.debug("Signal: {}", text);

		if (config.chatSignals())
		{
			// SPAM is a filtered game message: hidden when the Game chat filter is "Filtered", but still
			// posted as a ChatMessage event, so Watchdog (type Spam or Any) sees it. Plain text in the
			// default filtered-message colour (contracts/signals.md)
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.SPAM)
				.runeLiteFormattedMessage(text)
				.build());
		}

		// Notifier skips this when the notification is disabled
		notifier.notify(config.notificationSignals(), text);
	}

	/**
	 * Plain chat feedback that is not a signal (e.g. pause confirmation). It deliberately does not start
	 * with the signal prefix, so Watchdog patterns for signals never match it. Sent as a normal game
	 * message, not filtered, because the player asked for it.
	 */
	public void info(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private String templateFor(SignalType type)
	{
		switch (type)
		{
			case INACTIVE:
				return config.templateInactive();
			case ACTIVE:
				return config.templateActive();
			case STILL_INACTIVE:
				return config.templateReminder();
			case RESOURCE_ALERT:
				return config.templateResourceAlert();
			case RESOURCE_RECOVERED:
				return config.templateResourceRecovered();
			case IDLE_LOGOUT:
				return config.templateIdleLogout();
			case SIX_HOUR:
				return config.templateSixHour();
			case DAMAGE_TAKEN:
				return config.templateDamageTaken();
			default:
				return "";
		}
	}
}
