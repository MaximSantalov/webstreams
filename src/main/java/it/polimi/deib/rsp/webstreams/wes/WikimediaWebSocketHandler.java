package it.polimi.deib.rsp.webstreams.wes;

import com.taxonic.carml.engine.RmlMapper;
import com.taxonic.carml.logical_source_resolver.JsonPathResolver;
import com.taxonic.carml.model.TermMap;
import com.taxonic.carml.model.TriplesMap;
import com.taxonic.carml.rdf_mapper.util.ImmutableCollectors;
import com.taxonic.carml.util.RmlMappingLoader;
import com.taxonic.carml.vocab.Rdf;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
    JavaSpark class of WebSocket which will publish the RDF Stream.
*/

@WebSocket
public class WikimediaWebSocketHandler {

    private final RmlMapper mapper;
    private final Set<TriplesMap> mapping;
    private String sender, msg;
    private List<Session> users = new ArrayList<>();

    public WikimediaWebSocketHandler(String mappingfile_path, Object... functions) {

        /*
         * Step (3): The mapping is here configured.
         * The mapping file is loaded in the RML.
         *
         */

        this.mapper =
                RmlMapper
                        .newBuilder()
                        .setLogicalSourceResolver(Rdf.Ql.JsonPath, new JsonPathResolver())
                        .addFunctions(functions)
                        .build();
        
        InputStream mappingFileStream = WikimediaWebSocketHandler.class.getResourceAsStream(mappingfile_path);

        this.mapping =
                RmlMappingLoader
                        .build()
                        .load(RDFFormat.TURTLE,
                                mappingFileStream);

    }

    @OnWebSocketConnect
    public void onConnect(Session user) throws Exception {
        this.users.add(user);
    }

    @OnWebSocketClose
    public void onClose(Session user, int statusCode, String reason) {
        this.users.remove(user);
    }

    @OnWebSocketMessage
    public void onMessage(Session user, String message) {
    }

    /*
     * Step (5): Here the data is converted in RDF stream before being pushed.
     */

    public void bindInputStream(String gdeltStream, ByteArrayInputStream byteArrayInputStream) {
        mapper.bindInputStream(gdeltStream, byteArrayInputStream);
        Set functionValueTriplesMaps = mapper.getTermMaps(mapping).filter((t) -> t.getFunctionValue() != null).map(TermMap::getFunctionValue).collect(ImmutableCollectors.toImmutableSet());
        Set<TriplesMap> refObjectTriplesMaps = mapper.getAllTriplesMapsUsedInRefObjectMap(mapping);

        mapping.stream()
                .filter((tm) -> !functionValueTriplesMaps.contains(tm) && !refObjectTriplesMaps.contains(tm))
                .flatMap(tm -> mapper.map(tm, refObjectTriplesMaps))
                .map(this::toJSONLD)
                .forEach(s -> users.stream()
                        .filter(Session::isOpen)
                        .map(Session::getRemote)
                        .forEach(session -> send(s, session)));

        mapper.clearSourceManager();

    }

    private String toJSONLD(Model model) {
        StringWriter stringWriter = new StringWriter();
        RDFWriter rdfWriter = Rio.createWriter(RDFFormat.TURTLE, stringWriter);

        //rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
        //rdfWriter.getWriterConfig().set(JSONLDSettings.OPTIMIZE, true);
        //rdfWriter.getWriterConfig().set(BasicWriterSettings.PRETTY_PRINT, true);

        model.setNamespace("gdelt", "http://gdelt.org/vocab/");
        model.setNamespace("gkg", "http://gdelt.org/gkg/");
        model.setNamespace("gdelti", "http://gdelt.org/instance/");
        model.setNamespace("rdfs", "http://www.w3.org/TR/rdf-schema/");
        Rio.write(model, rdfWriter);

        return stringWriter.toString();
    }

    private void send(String s, RemoteEndpoint session) {
        try {
            session.sendString(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addSource(String stream_name, String s) {

    }
}