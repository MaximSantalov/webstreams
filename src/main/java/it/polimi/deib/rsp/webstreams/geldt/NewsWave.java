package it.polimi.deib.rsp.webstreams.geldt;

import lombok.extern.log4j.Log4j;
import spark.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Log4j
public class NewsWave {

    private static final String spec = "http://data.gdeltproject.org/gdeltv2/lastupdate.txt";
    private static final String header_export = "GLOBALEVENTID\tSQLDATE\tMonthYear\tYear\tFractionDate\tActor1Code\tActor1Name\tActor1CountryCode\tActor1KnownGroupCode\tActor1EthnicCode\tActor1Religion1Code\tActor1Religion2Code\tActor1Type1Code\tActor1Type2Code\tActor1Type3Code\tActor2Code\tActor2Name\tActor2CountryCode\tActor2KnownGroupCode\tActor2EthnicCode\tActor2Religion1Code\tActor2Religion2Code\tActor2Type1Code\tActor2Type2Code\tActor2Type3Code\tIsRootEvent\tEventCode\tEventBaseCode\tEventRootCode\tQuadClass\tGoldsteinScale\tNumMentions\tNumSources\tNumArticles\tAvgTone\tActor1Geo_Type\tActor1Geo_FullName\tActor1Geo_CountryCode\tActor1Geo_ADM1Code\tActor1Geo_ADM2Code\tActor1Geo_Lat\tActor1Geo_Long\tActor1Geo_FeatureID\tActor2Geo_Type\tActor2Geo_FullName\tActor2Geo_CountryCode\tActor2Geo_ADM1Code\tActor2Geo_ADM2Code\tActor2Geo_Lat\tActor2Geo_Long\tActor2Geo_FeatureID\tActionGeo_Type\tActionGeo_FullName\tActionGeo_CountryCode\tActionGeo_ADM1Code\tActionGeo_ADM2Code\tActionGeo_Lat\tActionGeo_Long\tActionGeo_FeatureID\tDATEADDED\tSOURCEURL";
    private static final String mapping_export = "export.ttl";
    private static final String header_mentions = "GLOBALEVENTID\tEventTimeDate\tMentionTimeDate\tMentionType\tMentionSourceName\tMentionIdentifier\tSentenceID\tActor1CharOffset\tActor2CharOffset\tActionCharOffset\tInRawText\tConfidence\tMentionDocLen\tMentionDocTone\tMentionDocTranslationInfo\tExtras";

    private static final String mapping_mentions = "mentions.ttl";
    private static final String header_gkg = "GKGRECORDID\tDATE\tSourceCollectionIdentifier\tSourceCommonName\tDocumentIdentifier\tCounts\tV2Counts\tThemes\tV2Themes\tLocations\tV2Locations\tPersons\tV2Persons\tOrganizations\tV2Organizations\tV2Tone\tDates\tGCAM\tSharingImage\tRelatedImages\tSocialImageEmbeds\tSocialVideoEmbeds\tQuotations\tAllNames\tAmounts\tTranslationInfo\tExtras";
    private static final String mapping_gkg = "gkg.ttl";
    private static GELDTWebSocketHandler handler;

    private static final String gkg1 = "gkg";
    private static final String mentions1 = "mentions";
    private static final String export = "events";
    private static final String export_name = "export";
    private static final String sgraph = "sgraph";

    private static final int sgraph_port = 80;
    private static final int sgraph_thread = 10;
    private static final int stream_port = 8080;
    private static final int stream_thread = 20;

    private static final String apimethod = "GET";
    public static final String stream_name = "GELDTStream";

    public static void main(String[] args) throws IOException {


        Service service1 = Service.ignite().port(sgraph_port).threadPool(sgraph_thread);
        Service service2 = Service.ignite().port(stream_port).threadPool(stream_thread);

        service1.get(File.separator + sgraph, (req, res) -> {
            return "";
            //TODO
        });

        GELDTWebSocketHandler events;
        service2.webSocket(File.separator + export, events = new GELDTWebSocketHandler(header_export, mapping_export, '\t'));

        GELDTWebSocketHandler mentions;
        service2.webSocket(File.separator + mentions1, mentions = new GELDTWebSocketHandler(header_mentions, mapping_mentions, '\t'));

        GELDTWebSocketHandler gkg;
        service2.webSocket(File.separator + gkg1, gkg = new GELDTWebSocketHandler(header_gkg, mapping_gkg, '\t'));
        service1.init();
        service2.init();

        URL dest = NewsWave.class.getResource("/csv/");

        URL url = new URL(spec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(apimethod);

        BufferedReader br;
        if (200 <= connection.getResponseCode() && connection.getResponseCode() <= 299) {
            br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        } else {
            br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        String line;
        while ((line = br.readLine()) != null) {
            String todownload = line.split(" ")[2];

            URL todownload_url = new URL(todownload);
            HttpURLConnection connection1 = (HttpURLConnection) todownload_url.openConnection();
            connection1.setRequestMethod(apimethod);

            InputStream inputStream = connection1.getInputStream();
            ZipInputStream zis = new ZipInputStream(inputStream);

            ByteArrayOutputStream dos = new ByteArrayOutputStream();

            byte[] buffer = new byte[5000];

            ZipEntry ze = zis.getNextEntry();

            log.info(ze.getName());

            if (ze.getName().contains(export_name))
                handler = events;
            else if (ze.getName().contains(mentions1))
                handler = mentions;
            else if (ze.getName().contains(gkg1))
                handler = gkg;
            else
                continue;

            while (ze != null) {
                int len;
                File f = new File(dest.getPath() + File.separator + ze.getName());
                FileOutputStream fos = new FileOutputStream(f);
                log.info("saving file at [" + f.getAbsolutePath() + "]");
                while ((len = zis.read(buffer)) > 0) {
                    dos.write(buffer, 0, len);
                    //save to a file
                    fos.write(buffer, 0, len);
                }
                dos.close();
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();

            handler.bindInputStream(stream_name, new ByteArrayInputStream(dos.toByteArray()));

        }

    }
}