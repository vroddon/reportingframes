package celestine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

/**
 * Converts a resolved Celestine annotation (JSON) to Turtle RDF,
 * using Framester URIs for frames and a local namespace for annotations.
 */
public class FramesterSerializer {

    // Framester frame namespace
    private static final String FST  = "https://w3id.org/framester/framenet/abox/frame/";
    // Celestine annotation namespace
    private static final String CEL  = "https://celestine.linkeddata.es/annotation/";
    // Celestine ontology namespace
    private static final String CELO = "https://celestine.linkeddata.es/ontology/";
    // NIF core ontology
    private static final String NIF  = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#";

    public static String toTurtle(String resolvedJson) {
        JsonObject root = JsonParser.parseString(resolvedJson).getAsJsonObject();

        String sentence = root.has("sentence") && !root.get("sentence").isJsonNull()
                ? root.get("sentence").getAsString() : "";

        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("fst",  FST);
        model.setNsPrefix("cel",  CEL);
        model.setNsPrefix("celo", CELO);
        model.setNsPrefix("nif",  NIF);
        model.setNsPrefix("xsd",  XSD.getURI());

        // Celestine ontology properties
        Property onText      = model.createProperty(CELO + "onText");
        Property hasFrame    = model.createProperty(CELO + "hasFrame");
        Property hasTarget   = model.createProperty(CELO + "hasTarget");
        Property hasFE       = model.createProperty(CELO + "hasFE");
        Property feName      = model.createProperty(CELO + "feName");
        Property incorporated = model.createProperty(CELO + "incorporated");
        Property annotatedAt = model.createProperty(CELO + "annotatedAt");

        // NIF properties for text spans
        Property nifBegin   = model.createProperty(NIF + "beginIndex");
        Property nifEnd     = model.createProperty(NIF + "endIndex");
        Property nifAnchor  = model.createProperty(NIF + "anchorOf");
        Resource nifString  = model.createResource(NIF + "String");

        Resource annotationClass = model.createResource(CELO + "Annotation");
        Resource feClass         = model.createResource(CELO + "FrameElementAnnotation");

        if (!root.has("frames") || root.get("frames").isJsonNull()) {
            return modelToTurtle(model);
        }

        JsonArray frames = root.getAsJsonArray("frames");
        for (JsonElement el : frames) {
            JsonObject frameObj = el.getAsJsonObject();
            if (!frameObj.has("frame") || frameObj.get("frame").isJsonNull()) continue;

            String frameName = frameObj.get("frame").getAsString();
            String annId = UUID.randomUUID().toString().substring(0, 8);

            Resource ann = model.createResource(CEL + "ann_" + annId);
            ann.addProperty(RDF.type, annotationClass);
            ann.addProperty(onText, sentence);
            ann.addProperty(annotatedAt, model.createTypedLiteral(Instant.now().toString(), XSD.dateTime.getURI()));
            ann.addProperty(hasFrame, model.createResource(FST + frameName));

            // Target
            if (frameObj.has("target") && !frameObj.get("target").isJsonNull()) {
                JsonElement targetEl = frameObj.get("target");
                if (targetEl.isJsonObject()) {
                    JsonObject t = targetEl.getAsJsonObject();
                    if (t.has("text")) ann.addProperty(hasTarget, t.get("text").getAsString());
                } else {
                    ann.addProperty(hasTarget, targetEl.getAsString());
                }
            }

            // Frame elements
            if (frameObj.has("fes") && frameObj.get("fes").isJsonArray()) {
                for (JsonElement feEl : frameObj.getAsJsonArray("fes")) {
                    JsonObject fe = feEl.getAsJsonObject();
                    Resource feRes = model.createResource();
                    feRes.addProperty(RDF.type, feClass);
                    feRes.addProperty(RDF.type, nifString);

                    if (fe.has("name"))
                        feRes.addProperty(feName, fe.get("name").getAsString());

                    if (fe.has("incorporated") && fe.get("incorporated").getAsBoolean()) {
                        feRes.addProperty(incorporated, model.createTypedLiteral(true));
                    } else {
                        if (fe.has("text"))
                            feRes.addProperty(nifAnchor, fe.get("text").getAsString());
                        if (fe.has("start") && fe.get("start").getAsInt() >= 0)
                            feRes.addProperty(nifBegin,
                                model.createTypedLiteral(fe.get("start").getAsInt(), XSD.nonNegativeInteger.getURI()));
                        if (fe.has("end") && fe.get("end").getAsInt() >= 0)
                            feRes.addProperty(nifEnd,
                                model.createTypedLiteral(fe.get("end").getAsInt(), XSD.nonNegativeInteger.getURI()));
                    }

                    ann.addProperty(hasFE, feRes);
                }
            }
        }

        return modelToTurtle(model);
    }

    private static String modelToTurtle(Model model) {
        StringWriter sw = new StringWriter();
        RDFDataMgr.write(sw, model, Lang.TURTLE);
        return sw.toString();
    }
}
