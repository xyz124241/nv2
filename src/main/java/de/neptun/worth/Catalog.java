package de.neptun.worth;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liste aller Items, Kategorien, Farben und Suchtexte (Englisch + Deutsch). */
public final class Catalog {

    private Catalog() {
    }

    public enum Category {
        ALL("Alle", Material.CHEST),
        BLOCKS("Blöcke", Material.GRASS_BLOCK),
        TOOLS("Werkzeuge", Material.IRON_PICKAXE),
        COMBAT("Kampf & Rüstung", Material.IRON_SWORD),
        FOOD("Essen", Material.COOKED_BEEF),
        REDSTONE("Redstone & Technik", Material.REDSTONE),
        SPAWN_EGGS("Spawn-Eier", Material.ZOMBIE_SPAWN_EGG),
        MISC("Sonstiges", Material.STICK);

        public final String label;
        public final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public enum Sort {
        NAME_ASC("Name A → Z"),
        NAME_DESC("Name Z → A"),
        PRICE_ASC("Preis: billigste zuerst"),
        PRICE_DESC("Preis: teuerste zuerst"),
        COLOR("Farbe"),
        CATEGORY("Kategorie"),
        STACK("Stapelgröße");

        public final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private static List<Material> all;

    /** Jedes Item im Spiel (ohne Legacy-Materialien und Luft). */
    public static synchronized List<Material> all() {
        if (all == null) {
            List<Material> list = new ArrayList<>();
            for (Material m : Material.values()) {
                if (m.isLegacy() || m.isAir() || !m.isItem()) {
                    continue;
                }
                list.add(m);
            }
            list.sort(Comparator.comparing(Material::name));
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    // ------------------------------------------------------------------ Kategorien

    private static boolean has(String n, String... parts) {
        for (String p : parts) {
            if (n.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static Category categoryOf(Material m) {
        String n = m.name();
        if (n.endsWith("_SPAWN_EGG")) {
            return Category.SPAWN_EGGS;
        }
        if (n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.endsWith("_HORSE_ARMOR") || n.endsWith("_SPEAR")
                || n.equals("BOW") || n.equals("CROSSBOW")
                || has(n, "TRIDENT", "SHIELD", "ARROW", "MACE", "ELYTRA", "WOLF_ARMOR", "TOTEM", "WIND_CHARGE")) {
            return Category.COMBAT;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || has(n, "FISHING_ROD", "SHEARS", "FLINT_AND_STEEL", "BRUSH", "SPYGLASS", "ON_A_STICK", "BUCKET",
                "COMPASS", "CLOCK", "NAME_TAG", "LEAD", "SADDLE")) {
            return Category.TOOLS;
        }
        if (m.isEdible()) {
            return Category.FOOD;
        }
        if (has(n, "REDSTONE", "PISTON", "REPEATER", "COMPARATOR", "OBSERVER", "DISPENSER", "DROPPER", "HOPPER",
                "LEVER", "_BUTTON", "PRESSURE_PLATE", "TRIPWIRE", "TARGET", "DAYLIGHT_DETECTOR", "RAIL", "TNT",
                "NOTE_BLOCK", "SCULK_SENSOR", "LIGHTNING_ROD", "CRAFTER", "BELL")) {
            return Category.REDSTONE;
        }
        if (m.isBlock()) {
            return Category.BLOCKS;
        }
        return Category.MISC;
    }

    // ------------------------------------------------------------------ Farben

    private static final String[] COLOR_ORDER = {
            "WHITE", "LIGHT_GRAY", "GRAY", "BLACK", "BROWN", "RED", "ORANGE", "YELLOW", "LIME", "GREEN",
            "CYAN", "LIGHT_BLUE", "BLUE", "PURPLE", "MAGENTA", "PINK"
    };

    /** Reihenfolge der Farbe (Regenbogen). Items ohne Farbe kommen ans Ende. */
    public static int colorIndex(Material m) {
        String padded = "_" + m.name() + "_";
        for (int i = 0; i < COLOR_ORDER.length; i++) {
            if (padded.contains("_" + COLOR_ORDER[i] + "_")) {
                return i;
            }
        }
        return 99;
    }

    // ------------------------------------------------------------------ Namen / Suche

    private static final Map<String, String> DE = new HashMap<>();

    static {
        String data = "diamond=diamant;iron=eisen;gold=gold;golden=gold;netherite=netherit;stone=stein;cobblestone=bruchstein;"
                + "deepslate=tiefenschiefer;cobbled=bruch;wooden=holz;wood=holz;planks=bretter;log=stamm;stripped=entrindet;"
                + "oak=eiche;spruce=fichte;birch=birke;jungle=dschungel;acacia=akazie;dark=dunkel;mangrove=mangrove;"
                + "cherry=kirsche;pale=blass;bamboo=bambus;crimson=karmesin;warped=wirr;pickaxe=spitzhacke;axe=axt;"
                + "shovel=schaufel;hoe=hacke;sword=schwert;helmet=helm;chestplate=brustplatte;leggings=hose;boots=stiefel;"
                + "bow=bogen;crossbow=armbrust;arrow=pfeil;shield=schild;fishing=angel;rod=rute;apple=apfel;bread=brot;"
                + "beef=rindfleisch;porkchop=schweinekotelett;chicken=huhn;mutton=hammelfleisch;rabbit=kaninchen;cod=kabeljau;"
                + "salmon=lachs;potato=kartoffel;carrot=karotte;beetroot=rote_bete;melon=melone;pumpkin=kuerbis;cake=kuchen;"
                + "cookie=keks;honey=honig;sugar=zucker;egg=ei;wheat=weizen;seeds=samen;sapling=setzling;leaves=laub;"
                + "flower=blume;grass=gras;dirt=erde;sand=sand;gravel=kies;clay=ton;glass=glas;pane=scheibe;wool=wolle;"
                + "carpet=teppich;bed=bett;banner=banner;door=tuer;trapdoor=falltuer;fence=zaun;gate=tor;stairs=treppe;"
                + "slab=stufe;wall=mauer;button=knopf;plate=platte;pressure=druck;lever=hebel;torch=fackel;lantern=laterne;"
                + "chest=truhe;barrel=fass;furnace=ofen;smoker=raeucherofen;blast=hoch;table=tisch;crafting=werkbank;"
                + "anvil=amboss;enchanting=verzauberung;enchanted=verzaubert;brewing=brau;stand=staender;cauldron=kessel;"
                + "bucket=eimer;water=wasser;lava=lava;milk=milch;coal=kohle;charcoal=holzkohle;copper=kupfer;emerald=smaragd;"
                + "lapis=lapis;lazuli=lazuli;redstone=redstone;quartz=quarz;amethyst=amethyst;obsidian=obsidian;"
                + "netherrack=netherrack;glowstone=leuchtstein;prismarine=prismarin;ender=ender;eye=auge;pearl=perle;"
                + "blaze=lohe;powder=pulver;slime=schleim;ball=ball;bone=knochen;meal=mehl;string=faden;feather=feder;"
                + "leather=leder;flint=feuerstein;gunpowder=schiesspulver;spider=spinnen;ghast=ghast;tear=traene;nether=nether;"
                + "star=stern;shard=splitter;ingot=barren;nugget=nugget;block=block;ore=erz;raw=roh;dye=farbstoff;"
                + "white=weiss;orange=orange;magenta=magenta;light=hell;blue=blau;yellow=gelb;lime=hellgruen;pink=rosa;"
                + "gray=grau;cyan=tuerkis;purple=lila;brown=braun;green=gruen;red=rot;black=schwarz;book=buch;"
                + "writable=beschreibbar;written=beschrieben;map=karte;compass=kompass;clock=uhr;spawn=spawn;saddle=sattel;"
                + "horse=pferd;armor=ruestung;minecart=lore;rail=schiene;boat=boot;sign=schild;painting=gemaelde;frame=rahmen;"
                + "head=kopf;skull=schaedel;skeleton=skelett;music=musik;disc=schallplatte;potion=trank;splash=wurf;"
                + "lingering=verweil;tipped=getraenkt;totem=totem;trident=dreizack;mace=streitkolben;elytra=elytren;"
                + "firework=feuerwerk;rocket=rakete;lead=leine;name=namens;tag=schild;bundle=buendel;shulker=shulker;box=kiste;"
                + "sponge=schwamm;wet=nass;mud=schlamm;brick=ziegel;bricks=ziegel;tile=kachel;terracotta=terrakotta;"
                + "glazed=glasiert;concrete=beton;stained=gefaerbt;coral=koralle;kelp=seetang;sea=see;sculk=sculk;moss=moos;"
                + "azalea=azalee;vine=ranke;lily=seerose;fern=farn;dripstone=tropfstein;pointed=spitz;lightning=blitz;"
                + "mushroom=pilz;cactus=kaktus;sugar_cane=zuckerrohr;cane=rohr;sweet=suess;berries=beeren;berry=beere;"
                + "glow=leucht;ink=tinte;sac=beutel;cobweb=spinnennetz;web=netz;snow=schnee;snowball=schneeball;ice=eis;"
                + "packed=gepackt;blue_ice=blaueis;magma=magma;soul=seelen;basalt=basalt;blackstone=schwarzstein;"
                + "polished=poliert;smooth=glatt;cut=geschnitten;chiseled=gemeisselt;cracked=rissig;mossy=bemoost;"
                + "end=end;purpur=purpur;chorus=chorus;fruit=frucht;shell=muschel;scute=panzer;turtle=schildkroete;"
                + "phantom=phantom;membrane=membran;nautilus=nautilus;heart=herz;sea_pickle=seegurke;pickle=gurke;"
                + "beacon=leuchtfeuer;conduit=aquisator;respawn=wiederbelebung;anchor=anker;lodestone=leitstein;"
                + "netherite_upgrade=netherit_upgrade;upgrade=upgrade;template=vorlage;smithing=schmiede;sherd=scherbe;"
                + "pottery=toepfer;pot=topf;flower_pot=blumentopf;decorated=verziert;trial=prüfung;key=schluessel;"
                + "ominous=unheilvoll;bottle=flasche;experience=erfahrung;dragon=drache;breath=atem;wither=wither;"
                + "creeper=creeper;zombie=zombie;piglin=piglin;villager=dorfbewohner;iron_golem=eisengolem;golem=golem;"
                + "cat=katze;dog=hund;wolf=wolf;bee=biene;cow=kuh;pig=schwein;sheep=schaf;fox=fuchs;panda=panda;"
                + "parrot=papagei;frog=frosch;axolotl=axolotl;allay=hilfsgeist;glow_squid=leuchttintenfisch;squid=tintenfisch;"
                + "bat=fledermaus;camel=kamel;goat=ziege;horn=horn;llama=lama;mule=maultier;donkey=esel;strider=schreiter;"
                + "hoglin=hoglin;guardian=waechter;elder=aelterer;drowned=ertrunkener;husk=wuestenzombie;stray=eiswanderer;"
                + "pillager=pluenderer;vindicator=diener;evoker=magier;ravager=verwuester;witch=hexe;vex=plagegeist;"
                + "enderman=enderman;endermite=endermite;silverfish=silberfischchen;slime_block=schleimblock;"
                + "tnt=tnt;observer=beobachter;piston=kolben;sticky=klebrig;hopper=trichter;dropper=werfer;dispenser=spender;"
                + "repeater=verstaerker;comparator=komparator;detector=sensor;daylight=tageslicht;tripwire=stolperdraht;"
                + "hook=haken;target=zielscheibe;note=noten;jukebox=plattenspieler;bell=glocke;campfire=lagerfeuer;"
                + "lectern=lesepult;loom=webstuhl;grindstone=schleifstein;stonecutter=steinsaege;cartography=kartografie;"
                + "fletching=pfeilmacher;smithing_table=schmiedetisch;composter=komposter;beehive=bienenstock;nest=nest;"
                + "honeycomb=wabe;comb=wabe;candle=kerze;spyglass=fernrohr;shears=schere;brush=pinsel;"
                + "mace=streitkolben;breeze=windboe;wind=wind;charge=ladung;heavy=schwer;core=kern;vault=tresor;"
                + "crafter=werkstatt;copper_bulb=kupferlampe;bulb=lampe;grate=gitter;tuff=tuff;calcite=calcit;"
                + "dripleaf=tropfblatt;spore=sporen;blossom=bluete;rooted=bewurzelt;roots=wurzeln;hanging=haengend;"
                + "shroomlight=pilzlicht;nylium=nylium;stem=stiel;hyphae=hyphen;wart=warze;fungus=pilz;weeping=heulend;"
                + "twisting=gedreht;vines=ranken;cave=hoehlen;spore_blossom=sporenbluete;big=gross;small=klein;"
                + "medium=mittel;large=gross;bud=knospe;cluster=cluster;budding=knospend;golden_apple=goldener_apfel;"
                + "carrot_on_a_stick=angel_mit_karotte;stick=stock;paper=papier;bowl=schuessel;stew=eintopf;"
                + "soup=suppe;rotten=verrottet;flesh=fleisch;cooked=gebraten;baked=gebacken;dried=getrocknet;"
                + "spectral=spektral;tinted=getoent;lodestone_compass=leitstein;recovery=bergungs;echo=echo;"
                + "disc_fragment=plattenfragment;fragment=fragment;harness=geschirr;horse_armor=pferderuestung;"
                + "gunpowder=schiesspulver;fire=feuer;charge=ladung;glow_ink=leuchttinte;item=item;display=anzeige;"
                + "armor_stand=ruestungsstaender;stand=staender;end_crystal=endkristall;crystal=kristall;end_rod=endstab;"
                + "end_stone=endstein;stone_bricks=steinziegel;infested=befallen;reinforced=verstaerkt;petrified=versteinert;"
                + "command=befehls;structure=struktur;barrier=barriere;jigsaw=puzzle;debug=debug;spawner=spawner;"
                + "bedrock=grundgestein;farmland=ackerboden;path=pfad;podzol=podsol;mycelium=myzel;coarse=grob;"
                + "rooted_dirt=bewurzelte_erde;packed_mud=gepackter_schlamm;mud_bricks=lehmziegel;bales=ballen;hay=heu;"
                + "dried_kelp=getrockneter_seetang;seagrass=seegras;tall=hohes;double=doppel;lilac=flieder;rose=rose;"
                + "peony=pfingstrose;sunflower=sonnenblume;dandelion=loewenzahn;poppy=mohn;tulip=tulpe;orchid=orchidee;"
                + "allium=lauch;azure=azur;bluet=porzellansternchen;oxeye=margerite;daisy=gaensebluemchen;cornflower=kornblume;"
                + "lily_of_the_valley=maiglockchen;valley=tal;wither_rose=witherrose;torchflower=fackellilie;"
                + "pitcher=krug;plant=pflanze;pod=huelse;sniffer=schnueffler;egg_sniffer=schnueffler_ei;"
                + "chain=kette;chainmail=kettenhemd;iron_bars=eisengitter;bars=gitter;ladder=leiter;scaffolding=geruest;"
                + "lodestone=leitstein;respawn_anchor=wiederbelebungsanker;cauldron=kessel;powder_snow=pulverschnee;"
                + "wax=wachs;waxed=gewachst;exposed=angelaufen;weathered=verwittert;oxidized=oxidiert;lightning_rod=blitzableiter;"
                + "spyglass=fernrohr;elder_guardian=aelterer_waechter;raw_iron=roheisen;raw_gold=rohgold;raw_copper=rohkupfer";
        for (String pair : data.split(";")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                DE.put(pair.substring(0, i), pair.substring(i + 1));
            }
        }
    }

    private static final Map<Material, String> SEARCH = new HashMap<>();
    private static final Map<Material, String> SORT = new HashMap<>();

    /** Deutscher Anzeigename (so gut es das Wörterbuch hergibt), kleingeschrieben. */
    public static synchronized String germanName(Material m) {
        String cached = SORT.get(m);
        if (cached != null) {
            return cached;
        }
        StringBuilder sb = new StringBuilder();
        for (String token : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(DE.getOrDefault(token, token));
        }
        String s = sb.toString();
        SORT.put(m, s);
        return s;
    }

    /** Alles, worin gesucht wird: englische Namen + deutsche Namen. */
    public static synchronized String searchText(Material m) {
        String cached = SEARCH.get(m);
        if (cached != null) {
            return cached;
        }
        String english = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String s = english + " " + m.name().toLowerCase(Locale.ROOT) + " " + germanName(m)
                + " " + germanName(m).replace("ae", "ä").replace("oe", "ö").replace("ue", "ü").replace("ss", "ß");
        SEARCH.put(m, s);
        return s;
    }
}
