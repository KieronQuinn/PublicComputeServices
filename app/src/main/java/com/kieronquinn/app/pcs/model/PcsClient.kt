package com.kieronquinn.app.pcs.model

import com.google.android.`as`.oss.pd.api.proto.BlobConstraints.Client

enum class PcsClient(
    val client: Client,
    val clientId: String,
    /**
     *  Build IDs are used to deliver the version of the model to use over Phenotypes
     */
    val buildId: BuildId
) {
    /*
    // No longer used: https://github.com/google/private-compute-services/commit/59b4b7b66351b08be71abee92bf437984f29a725
    SUSPICIOUS_MESSAGE_ALERTS("com.google.android.as"),
    PLAY_PROTECT_SERVICE("com.google.android.PlayProtect"),
    PLAY_PROTECT_SERVICE_CORE_DEFAULT("com.google.android.PlayProtect:2793571637033546290"),
    AI_CORE_PROTECTED_DOWNLOAD("com.google.android.aicore:11791126134479005147"),
    AI_CORE_TEXT_INPUT("com.google.android.aicore:3649180271731021675"),
    AI_CORE_IMAGE_OUTPUT("com.google.android.aicore:16223496253676012401"),
    PLAY_PROTECT_SERVICE_PVM_DEFAULT("com.google.android.PlayProtect:2525461103339185322"),
    AI_CORE_TEXT_OUTPUT("com.google.android.aicore:7923848966216590666"),
    AI_CORE_IMAGE_INPUT("com.google.android.aicore:6120135725815620389"),
    AI_CORE_MESSAGES_TEXT("com.google.android.aicore:4970947506931743799"),
    AI_CORE_CHROME_SUMMARIZATION_OUTPUT("com.google.android.aicore:8519285862245230442"),
    AI_CORE_CLIENT_12("com.google.android.aicore:418124939180967388"),
    AI_CORE_CLIENT_13("com.google.android.aicore:15018369527000359173"),
    AI_CORE_CLIENT_14("com.google.android.aicore:10085173703611871103"),*/
    AI_CORE_CLIENT_15(
        Client.AI_CORE_CLIENT_15,
        "com.google.android.aicore:14589082030786492895",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_14589082030786492895")
    ),
    AI_CORE_CLIENT_16(
        Client.AI_CORE_CLIENT_16,
        "com.google.android.aicore:5333321975141516928",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_5333321975141516928")
    ),
    AI_CORE_CLIENT_17(
        Client.AI_CORE_CLIENT_17,
        "com.google.android.aicore:9353767029546147385",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_9353767029546147385")
    ),
    AI_CORE_CLIENT_18(
        Client.AI_CORE_CLIENT_18,
        "com.google.android.aicore:10167985913044593434",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_10167985913044593434")
    ),
    AI_CORE_CLIENT_19(
        Client.AI_CORE_CLIENT_19,
        "com.google.android.aicore:3561907884583738100",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_3561907884583738100")
    ),
    AI_CORE_CLIENT_20(
        Client.AI_CORE_CLIENT_20,
        "com.google.android.aicore:4870111188580693201",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_4870111188580693201")
    ),
    AI_CORE_CLIENT_21(
        Client.AI_CORE_CLIENT_21,
        "com.google.android.aicore:6642565339740637386",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_6642565339740637386")
    ),
    AI_CORE_CLIENT_22(
        Client.AI_CORE_CLIENT_22,
        "com.google.android.aicore:9931783747856508885",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_9931783747856508885")
    ),
    AI_CORE_CLIENT_23(
        Client.AI_CORE_CLIENT_23,
        "com.google.android.aicore:5848825322855942324",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_5848825322855942324")
    ),
    AI_CORE_CLIENT_24(
        Client.AI_CORE_CLIENT_24,
        "com.google.android.aicore:4341791953025243445",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_4341791953025243445")
    ),
    AI_CORE_CLIENT_25(
        Client.AI_CORE_CLIENT_25,
        "com.google.android.aicore:6417633745608261729",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_6417633745608261729")
    ),
    AI_CORE_CLIENT_26(
        Client.AI_CORE_CLIENT_26,
        "com.google.android.aicore:11720962012422846819",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_11720962012422846819")
    ),
    AI_CORE_CLIENT_27(
        Client.AI_CORE_CLIENT_27,
        "com.google.android.aicore:14254786987761682043",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_14254786987761682043")
    ),
    AI_CORE_CLIENT_28(
        Client.AI_CORE_CLIENT_28,
        "com.google.android.aicore:4027292349711707490",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_4027292349711707490")
    ),
    AI_CORE_CLIENT_29(
        Client.AI_CORE_CLIENT_29,
        "com.google.android.aicore:1558569612950046780",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_1558569612950046780")
    ),
    AI_CORE_CLIENT_30(
        Client.AI_CORE_CLIENT_30,
        "com.google.android.aicore:6109265619551471570",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_6109265619551471570")
    ),
    AI_CORE_CLIENT_31(
        Client.AI_CORE_CLIENT_31,
        "com.google.android.aicore:6098232831121113138",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_6098232831121113138")
    ),
    AI_CORE_CLIENT_32(
        Client.AI_CORE_CLIENT_32,
        "com.google.android.aicore:14604084352937090483",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_14604084352937090483")
    ),
    AI_CORE_CLIENT_33(
        Client.AI_CORE_CLIENT_33,
        "com.google.android.aicore:10230360187542661313",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_10230360187542661313")
    ),
    AI_CORE_CLIENT_34(
        Client.AI_CORE_CLIENT_34,
        "com.google.android.aicore:14144884036502714237",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_14144884036502714237")
    ),
    AI_CORE_CLIENT_35(
        Client.AI_CORE_CLIENT_35,
        "com.google.android.aicore:16512701228291749612",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_16512701228291749612")
    ),
    AI_CORE_CLIENT_36(
        Client.AI_CORE_CLIENT_36,
        "com.google.android.aicore:3701923067702114378",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_3701923067702114378")
    ),
    AI_CORE_CLIENT_37(
        Client.AI_CORE_CLIENT_37,
        "com.google.android.aicore:18103149225492435673",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_18103149225492435673")
    ),
    AI_CORE_CLIENT_38(
        Client.AI_CORE_CLIENT_38,
        "com.google.android.aicore:5398059663487363370",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_5398059663487363370")
    ),
    AI_CORE_CLIENT_39(
        Client.AI_CORE_CLIENT_39,
        "com.google.android.aicore:16093837962507438679",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_16093837962507438679")
    ),
    AI_CORE_CLIENT_40(
        Client.AI_CORE_CLIENT_40,
        "com.google.android.aicore:9945587330698106851",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_9945587330698106851")
    ),
    AI_CORE_CLIENT_41(
        Client.AI_CORE_CLIENT_41,
        "com.google.android.aicore:9347763061896501379",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_9347763061896501379")
    ),
    AI_CORE_CLIENT_42(
        Client.AI_CORE_CLIENT_42,
        "com.google.android.aicore:10553225535939326565",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_10553225535939326565")
    ),
    AI_CORE_CLIENT_43(
        Client.AI_CORE_CLIENT_43,
        "com.google.android.aicore:5742606038786011969",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_5742606038786011969")
    ),
    AI_CORE_CLIENT_44(
        Client.AI_CORE_CLIENT_44,
        "com.google.android.aicore:9614928112563494806",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_9614928112563494806")
    ),
    AI_CORE_CLIENT_45(
        Client.AI_CORE_CLIENT_45,
        "com.google.android.aicore:6824732181910573706",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_6824732181910573706")
    ),
    AI_CORE_CLIENT_46(
        Client.AI_CORE_CLIENT_46,
        "com.google.android.aicore:7632259796561150258",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_7632259796561150258")
    ),
    AI_CORE_CLIENT_47(
        Client.AI_CORE_CLIENT_47,
        "com.google.android.aicore:12851944831581789857",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_12851944831581789857")
    ),
    AI_CORE_CLIENT_48(
        Client.AI_CORE_CLIENT_48,
        "com.google.android.aicore:17203260412298451912",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_17203260412298451912")
    ),
    AI_CORE_CLIENT_49(
        Client.AI_CORE_CLIENT_49,
        "com.google.android.aicore:2651730305904984656",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_2651730305904984656")
    ),
    AI_CORE_CLIENT_50(
        Client.AI_CORE_CLIENT_50,
        "com.google.android.aicore:5495164372972161668",
        BuildId(BuildId.Namespace.AICORE, "AicDataRelease__build_id_5495164372972161668")
    ),
    PSI_MDD_MODELS_CLIENT(
        Client.PSI_MDD_MODELS_CLIENT,
        "com.google.android.apps.pixel.psi:11791126134479005147",
        BuildId(BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES, "PsiModelDownload__psi_build_id_11791126134479005147")
    ),
    PSI_LLM_OUTPUT_CLASSIFIER_CLIENT(
        Client.PSI_LLM_OUTPUT_CLASSIFIER_CLIENT,
        "com.google.android.apps.pixel.psi:3177959871173576590",
        BuildId(BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES, "PsiModelDownload__psi_build_id_3177959871173576590")
    ),
    PSI_NON_LLM_OUTPUT_CLASSIFIER_CLIENT(
        Client.PSI_NON_LLM_OUTPUT_CLASSIFIER_CLIENT,
        "com.google.android.apps.pixel.psi:12033173399242171289",
        BuildId(BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES, "PsiModelDownload__psi_build_id_12033173399242171289")
    ),
    PSI_TEXT_INPUT_REGEXT_ONLY_CLASSIFIER_CLIENT(
        Client.PSI_TEXT_INPUT_REGEXT_ONLY_CLASSIFIER_CLIENT,
        "com.google.android.apps.pixel.psi:17453388543208459382",
        BuildId(BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES, "PsiModelDownload__psi_build_id_17453388543208459382")
    ),
    PSI_TEXT_OUTPUT_REGEX_ONLY_CLASSIFIER_CLIENT(
        Client.PSI_TEXT_OUTPUT_REGEX_ONLY_CLASSIFIER_CLIENT,
        "com.google.android.apps.pixel.psi:11987824919611589942",
        BuildId(BuildId.Namespace.DEVICE_PERSONALIZATION_SERVICES, "PsiModelDownload__psi_build_id_11987824919611589942")
    ),
    /*PROTECTED_DOWNLOAD_TESTING(99997),
    STRESSTEST_LEGACY(99998),
    STRESSTEST_CORE_DEFAULT(99999),
    SAFETY_CORE_PCC_TESTING(10000)*/
    ;

    data class BuildId(val namespace: Namespace, val flag: String) {
        enum class Namespace(val value: String) {
            AICORE("aicore"),
            DEVICE_PERSONALIZATION_SERVICES("device_personalization_services")
        }
    }
}