package extensions.sanctions

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.converters.impl.coalescingDefaultingString
import dev.kordex.core.commands.converters.impl.coalescingString
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.commands.converters.impl.member
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.utils.FilterStrategy
import dev.kordex.core.utils.suggestStringMap
import extensions.StatsPeriod
import fr.ayfri.rocketmanager.i18n.Translations
import storage.SanctionType
import kotlin.time.DurationUnit

/** Ready-made reasons offered by the autocompletion of every sanction command, keyed by the shorthand staff types. */
val sanctionReasons by lazy {
	mapOf(
		"fake pub" to Translations.Sanctions.fakePub,
		"invite reward" to Translations.Sanctions.inviteReward,
		"mauvaise catégorie" to Translations.Sanctions.wrongCategory,
		"mention everyone/here" to Translations.Sanctions.mentionEveryone,
		"pub interdite" to Translations.Sanctions.forbiddenAd,
		"pub mp" to Translations.Sanctions.dmAd,
		"pubs à la suite" to Translations.Sanctions.consecutiveAds,
		"sans description" to Translations.Sanctions.noDescription,
		"spam après warn" to Translations.Sanctions.spamAfterWarn,
		"spam" to Translations.Sanctions.spam,
	).mapValues { (_, value) -> value.translate() }
}

enum class DurationUnits(val durationUnit: DurationUnit) : ChoiceEnum {
	DAYS(DurationUnit.DAYS),
	HOURS(DurationUnit.HOURS),
	MINUTES(DurationUnit.MINUTES);

	override val readableName
		get() = when (this) {
			DAYS -> Translations.Units.days
			HOURS -> Translations.Units.hours
			MINUTES -> Translations.Units.minutes
		}
}

fun Arguments.sanctionMember() = member {
	name = Translations.Arguments.Member.name
	description = Translations.Arguments.Member.description
}

fun Arguments.sanctionUser() = user {
	name = Translations.Arguments.User.name
	description = Translations.Arguments.User.description
}

fun Arguments.sanctionReason() = coalescingString {
	name = Translations.Arguments.Reason.name
	description = Translations.Arguments.Reason.description
	autoComplete { suggestStringMap(sanctionReasons, FilterStrategy.Contains) }
}

fun Arguments.sanctionCaseId() = int {
	name = Translations.Arguments.Id.name
	description = Translations.Arguments.Id.description
}

fun Arguments.optionalSanctionType() = optionalEnumChoice<SanctionType> {
	name = Translations.Arguments.Type.name
	description = Translations.Arguments.Type.description
	typeName = Translations.Arguments.Type.name
}

fun Arguments.durationUnit() = enumChoice<DurationUnits> {
	name = Translations.Arguments.Unit.name
	description = Translations.Arguments.Unit.description
	typeName = Translations.Arguments.Unit.name
}

fun Arguments.optionalDurationUnit() = optionalEnumChoice<DurationUnits> {
	name = Translations.Arguments.Unit.name
	description = Translations.Arguments.Unit.description
	typeName = Translations.Arguments.Unit.name
}

class BanArguments : Arguments() {
	val member by sanctionMember()
	val reason by sanctionReason()

	val duration by optionalInt {
		name = Translations.Arguments.Duration.name
		description = Translations.Arguments.Duration.description
	}

	val deleteDays by optionalInt {
		name = Translations.Arguments.DeleteDays.name
		description = Translations.Arguments.DeleteDays.description

		maxValue = 7
		minValue = 1
	}

	val unit by optionalDurationUnit()
}

class KickArguments : Arguments() {
	val member by sanctionMember()
	val reason by sanctionReason()
}

class MuteArguments : Arguments() {
	val member by sanctionMember()

	val duration by int {
		name = Translations.Arguments.Duration.name
		description = Translations.Arguments.Duration.description
	}

	val unit by durationUnit()
	val reason by sanctionReason()
}

class WarnArguments : Arguments() {
	val member by sanctionMember()
	val reason by sanctionReason()
}

class UnBanArguments : Arguments() {
	val user by sanctionUser()

	val reason by coalescingDefaultingString {
		name = Translations.Arguments.Reason.name
		description = Translations.Arguments.Reason.description
		defaultValue = Translations.Messages.noReason.translate()
	}
}

class UnMuteArguments : Arguments() {
	val member by sanctionMember()
}

class DeleteSanctionArguments : Arguments() {
	val id by sanctionCaseId()
}

class DeleteAllSanctionsArguments : Arguments() {
	val user by sanctionUser()
	val type by optionalSanctionType()
}

class ListSanctionsArguments : Arguments() {
	val user by sanctionUser()
	val type by optionalSanctionType()
}

class SanctionInfoArguments : Arguments() {
	val user by sanctionUser()
}

class SearchSanctionsArguments : Arguments() {
	val user by optionalUser {
		name = Translations.Arguments.User.name
		description = Translations.Arguments.User.description
	}

	val moderator by optionalUser {
		name = Translations.Arguments.Moderator.name
		description = Translations.Arguments.Moderator.description
	}

	val type by optionalSanctionType()

	val period by optionalEnumChoice<StatsPeriod> {
		name = Translations.Arguments.Period.name
		description = Translations.Arguments.Period.description
		typeName = Translations.Arguments.Period.name
	}

	val reason by optionalString {
		name = Translations.Arguments.Reason.name
		description = Translations.Arguments.Reason.description
		autoComplete { suggestStringMap(sanctionReasons, FilterStrategy.Contains) }
	}
}
