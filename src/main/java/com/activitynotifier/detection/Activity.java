package com.activitynotifier.detection;

import lombok.Value;

/**
 * What the player was doing, as named in signals (FR-010a), and which detector saw it.
 */
@Value
public class Activity
{
	String name;
	Detector detector;
}
