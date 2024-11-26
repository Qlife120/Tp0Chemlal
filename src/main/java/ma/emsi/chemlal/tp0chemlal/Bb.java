package ma.emsi.chemlal.tp0chemlal;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation pendant plusieurs requêtes HTTP.
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    /**
     * Rôle "système" que l'on attribuera plus tard à un LLM.
     * Possible d'ajouter de nouveaux rôles dans la méthode getSystemRoles.
     */
    private String systemRole;
    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF) dans la même session de chat.
     */
    private boolean systemRoleChangeable = true;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;
    /**
     * Dernière réponse de l'API OpenAI.
     */
    private String reponse;
    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI).
     */
    public Bb() {
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public boolean isSystemRoleChangeable() {
        return systemRoleChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    /** Fonction qui detecte la langue de la question de l'utilisateur en utilisant le modele language detector OpenNlp
     *
      */

    public String detectLanguage(String question){
        if(question==null || question.isBlank()){
            return "Texte vide: Langue non detecte.";
        }
        try(InputStream modelIn = getClass().getResourceAsStream("/langdetect-183.bin")) {

            LanguageDetectorModel model = new LanguageDetectorModel(modelIn);
            LanguageDetector languageDetector = new LanguageDetectorME(model);

            Language detectedLanguage = languageDetector.predictLanguage(question);

            return detectedLanguage.getLang();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la detection de langue.",e);
        }
    }

    /**
     * Envoie la question au serveur.
     * En attendant de l'envoyer à un LLM, le serveur fait un traitement quelconque, juste pour tester :
     * Le traitement consiste à copier la question en minuscules et à l'entourer avec "||". Le rôle système
     * est ajouté au début de la première réponse.
     *
     * @return null pour rester sur la même page.
     */

    public String envoyer() {
        if (question == null || question.isBlank()) {
            // Erreur ! Le formulaire va être automatiquement réaffiché par JSF en réponse à la requête POST,
            // avec le message d'erreur donné ci-dessous.
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }
        // Si le role detecte est Detecteur de langue, respone prend le language detecte par le model, sinon le meme traitement.
        if ("You are a languge detector model.".equals(systemRole.trim())){
            this.reponse = detectLanguage(question);
        }
        else {
            if(this.conversation.isEmpty()){
                this.reponse = systemRole.toUpperCase(Locale.FRENCH) + "\n";

            }
            this.reponse += question.toLowerCase(Locale.FRENCH) + "||";

        }
        // La conversation contient l'historique des questions-réponses depuis le début.
        afficherConversation();
        return null;
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== Serveur:\n").append(reponse).append("\n");
    }

    public List<SelectItem> getSystemRoles() {
        List<SelectItem> listeSystemRoles = new ArrayList<>();
        // Ces rôles ne seront utilisés que lorsque la réponse sera données par un LLM.
        String role = """
                You are a helpful assistant. You help the user to find the information they need.
                If the user type a question, you answer it.
                """;
        listeSystemRoles.add(new SelectItem(role, "Assistant"));
        role = """
                You are an interpreter. You translate from English to French and from French to English.
                If the user type a French text, you translate it into English.
                If the user type an English text, you translate it into French.
                If the text contains only one to three words, give some examples of usage of these words in English.
                """;
        // 1er argument : la valeur du rôle, 2ème argument : le libellé du rôle
        listeSystemRoles.add(new SelectItem(role, "Traducteur Anglais-Français"));
        role = """
                Your are a travel guide. If the user type the name of a country or of a town,
                you tell them what are the main places to visit in the country or the town
                are you tell them the average price of a meal.
                """;
        listeSystemRoles.add(new SelectItem(role, "Guide touristique"));

        /** Ajout du noveau role juste pour faire la difference entre les autres roles deja predefinies, la detection de langue ne
         * se declenchera que quand ce role est selectionne.
         *
         */
        role = """
                You are a languge detector model. 
                """;

        listeSystemRoles.add(new SelectItem(role, "Detecteur de langue"));

        // Présélectionne le premier rôle de la liste.
                this.systemRole = (String) listeSystemRoles.getFirst().getValue();
        return listeSystemRoles;
    }


}
