package com.ibm.nexus.staging.rest;

import static org.sonatype.nexus.rest.repositories.AbstractRepositoryPlexusResource.REPOSITORY_ID_KEY;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.nexus.proxy.AccessDeniedException;
import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;
import org.sonatype.nexus.proxy.NoSuchResourceStoreException;
import org.sonatype.nexus.proxy.ResourceStore;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.StorageException;
import org.sonatype.nexus.proxy.access.Action;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.maven.ArtifactStoreHelper;
import org.sonatype.nexus.proxy.maven.ArtifactStoreRequest;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.maven.gav.Gav;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.rest.AbstractResourceStoreContentPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;

@SuppressWarnings("deprecation")
@Named(value = "copyfrom")
@Singleton
public class RepositoryArtifactCopyFrom extends AbstractResourceStoreContentPlexusResource {
	@Inject
	public RepositoryArtifactCopyFrom() {
		setReadable(true);
		setModifiable(true);
	}
	
	@Override
	public boolean acceptsUpload() {
		// we handle PUT method only
		return false;
	}

	@Override
	public String getResourceUri() {
		return "/repositories/{" + REPOSITORY_ID_KEY + "}/copyfrom";
	}

	@Override
	public PathProtectionDescriptor getResourceProtection() {
		return new PathProtectionDescriptor("/repositories/*/copyfrom/**", "authcBasic");
	}

	@Override
	public Object getPayloadInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ResourceStore getResourceStore(Request request) throws NoSuchResourceStoreException, ResourceException {
		return getUnprotectedRepositoryRegistry().getRepository(request.getAttributes().get(REPOSITORY_ID_KEY).toString());
	}
	
	@Override
	public Object get(Context context, Request request, Response response, Variant variant) throws ResourceException {
		try {
			
			final Form form = request.getResourceRef().getQueryAsForm();
			final String groupId = form.getFirstValue("g");
			final String artifactId = form.getFirstValue("a");
			final String version = form.getFirstValue("v");
			final String packaging = form.getFirstValue("p","jar");
			String extension = form.getFirstValue("e");
			String classifier = form.getFirstValue("c");
			final String repositoryId = form.getFirstValue("r");

			handleRequest(request, response, groupId, artifactId, version, classifier, packaging, extension, repositoryId);

			return response;
		} catch(Exception ex) {
			handleException(request, response, ex);
			return "";
		}
	}
	
	@Override 
	public Object put(Context context, Request request, Response response, Object payload) throws ResourceException {
		try {
			if(!request.getEntity().getMediaType().isCompatible(MediaType.APPLICATION_JSON)) {
				throw new ResourceException(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,"Put excepts application/json media types only.");
			}

			final String json = request.getEntity().getText().trim();

			if(json.startsWith("[")) {
				handleJsonArray(request, response, new JSONArray(json));
			} else {
				handleJsonObject(request, response, new JSONObject(json));
			}
			return response;
		} catch(Exception ex) {
			handleException(request,response,ex);
			return "";
		}
	}
	
	private void handleJsonArray(final Request request, final Response response, final JSONArray json) throws JSONException, NoSuchRepositoryException, ResourceException, StorageException, AccessDeniedException, IllegalOperationException, ItemNotFoundException, UnsupportedStorageOperationException {
		for(int index = 0; index < json.length(); ++index) {
			final JSONObject object = json.getJSONObject(index);
			handleJsonObject(request, response, object);
		}
	}
	
	private void handleJsonObject(final Request request, final Response response, final JSONObject json) throws JSONException, NoSuchRepositoryException, ResourceException, StorageException, AccessDeniedException, IllegalOperationException, ItemNotFoundException, UnsupportedStorageOperationException {
		final String groupId = json.optString("groupId");
		final String artifactId = json.optString("artifactId");
		final String version = json.optString("version");
		final String packaging = json.optString("packaging","jar");
		String extension = json.optString("extension");
		String classifier = json.optString("classifier");
		final String repositoryId = json.optString("repositoryId");
		
		handleRequest(request, response, groupId, artifactId, version, classifier, packaging, extension, repositoryId);
	}
	
	@Override
	public void delete(Context context, Request request, Response response) throws ResourceException{
		throw new ResourceException(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
	}
	
	private void handleRequest(final Request request, final Response response, final String groupId, final String artifactId, final String version, String classifier, final String packaging, String extension, final String repositoryId) throws NoSuchRepositoryException, ResourceException, StorageException, AccessDeniedException, IllegalOperationException, ItemNotFoundException, UnsupportedStorageOperationException, JSONException {
		final MavenRepository targetRepository = getMavenRepository(request.getAttributes().get(REPOSITORY_ID_KEY).toString());
		final MavenRepository sourceRepository = getMavenRepository(repositoryId);
		
		final String pomPackaging = "pom";
		
		if(StringUtils.isBlank(extension)) {
			extension = sourceRepository.getArtifactPackagingMapper().getExtensionForPackaging(packaging); 
		}
		
		if(StringUtils.isBlank(classifier)) {
			classifier = null;
		}
		
		final Gav gav = new Gav(groupId, artifactId, version, classifier, extension, null, null, null, false, null, false, null);
		
		final ArtifactStoreRequest artifactStoreRequest = new ArtifactStoreRequest(sourceRepository, gav, true);
		
		if(getLogger().isDebugEnabled()) {
			getLogger().debug("Create ArtifactStoreRequest for " + artifactStoreRequest.getRequestPath());
		}
		
		final ArtifactStoreHelper helper = sourceRepository.getArtifactStoreHelper();
		final Gav resolvedGav = helper.resolveArtifact(artifactStoreRequest);
		
		final String pomExtension = sourceRepository.getArtifactPackagingMapper().getExtensionForPackaging(pomPackaging);
		final Gav pomGav = new Gav(groupId, artifactId, version, "", pomExtension, null, null, null, false, null, false, null);
		
		final ArtifactStoreRequest pomArtifactStoreRequest = new ArtifactStoreRequest(sourceRepository, pomGav, true);
		
		if(getLogger().isDebugEnabled()) {
			getLogger().debug("Create ArtifactStoreRequest for " + artifactStoreRequest.getRequestPath());
		}
		
		final Gav pomResolvedGav = helper.resolveArtifact(pomArtifactStoreRequest);
		
		copyFrom(targetRepository, sourceRepository, resolvedGav, pomResolvedGav);
		final Response updateResponse = updateMetadata(request,targetRepository,groupId);
		if(updateResponse.getStatus().isError()) {
			response.setStatus(updateResponse.getStatus());
		}
		
		JSONObject json = new JSONObject();
		json.put("result", "SUCCESSFUL");
		response.setEntity(json.toString(), MediaType.APPLICATION_JSON);
		response.setStatus(Status.SUCCESS_CREATED);
	}
	
	private MavenRepository getMavenRepository(String repositoryId) throws NoSuchRepositoryException, ResourceException {
		Repository repository = this.getRepositoryRegistry().getRepository(repositoryId);
		MavenRepository mavenRepository;
		if(repository instanceof MavenRepository) {
			mavenRepository = (MavenRepository)repository;
		} else {
			throw new ResourceException( Status.CLIENT_ERROR_BAD_REQUEST, "Target Repository was not a maven repository.");
		}
		return mavenRepository;
	}
	
	private void copyFrom(final MavenRepository targetRepository, final MavenRepository sourceRepository, final Gav artifactGav, final Gav pomGav) throws StorageException, AccessDeniedException, IllegalOperationException, ItemNotFoundException, UnsupportedStorageOperationException {
		final ArtifactStoreRequest sourceStoreRequest = new ArtifactStoreRequest(sourceRepository, artifactGav, true);
		final ArtifactStoreRequest pomSourceStoreRequest = new ArtifactStoreRequest(sourceRepository, pomGav, true);
		
		final DefaultStorageFileItem sourceItem = (DefaultStorageFileItem) sourceRepository.getArtifactStoreHelper().retrieveArtifact(sourceStoreRequest);
		final DefaultStorageFileItem pomSourceItem = (DefaultStorageFileItem) sourceRepository.getArtifactStoreHelper().retrieveArtifact(pomSourceStoreRequest);
		
		ResourceStoreRequest rsr = new ResourceStoreRequest(sourceItem);
		targetRepository.getAccessManager().decide(targetRepository, rsr, Action.create);

		//AbstractRepository.storeItem creates a lock for these items 
		//DefaultFSLocalRepositoryStorage.storeItem is what is called after the items are locked
		//   this should now handle a bad upload.
		//Therefore, file fidelity should happen implicitly.
		targetRepository.storeItemWithChecksums(true, sourceItem);
		targetRepository.storeItemWithChecksums(true, pomSourceItem);
	}
	
	private Response updateMetadata(final Request request, MavenRepository repository, String groupId) {
		final Request updateMetadataRequest = new Request(Method.DELETE,request.getRootRef() + "/metadata/repositories/" + repository.getId() + "/content/" + groupId.replaceAll("\\.", "/"));
		updateMetadataRequest.setAttributes(request.getAttributes());
		updateMetadataRequest.setChallengeResponse(request.getChallengeResponse());
		updateMetadataRequest.setClientInfo(request.getClientInfo());
		updateMetadataRequest.setConditions(request.getConditions());
		updateMetadataRequest.setCookies(request.getCookies());
		updateMetadataRequest.setHostRef(request.getHostRef());
		updateMetadataRequest.setRootRef(request.getRootRef());

		final Client client = new Client(Protocol.HTTP);
		return client.handle(updateMetadataRequest);
	}
}
