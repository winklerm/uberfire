package org.uberfire.client.perspectives;

import javax.enterprise.context.ApplicationScoped;

import org.uberfire.client.annotations.Perspective;
import org.uberfire.client.annotations.WorkbenchPerspective;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.workbench.model.PanelDefinition;
import org.uberfire.workbench.model.PerspectiveDefinition;
import org.uberfire.workbench.model.Position;
import org.uberfire.workbench.model.impl.PanelDefinitionImpl;
import org.uberfire.workbench.model.impl.PartDefinitionImpl;
import org.uberfire.workbench.model.impl.PerspectiveDefinitionImpl;

@ApplicationScoped
@WorkbenchPerspective(identifier = "homePerspective", isDefault = true)
public class HomePerspective {

    @Perspective
    public PerspectiveDefinition buildPerspective() {
        final PerspectiveDefinition p = new PerspectiveDefinitionImpl();
        p.setToolbarVisible( false );
        p.setName( "Home" );

        final PanelDefinition west = new PanelDefinitionImpl();
        west.addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "YouTubeVideos" ) ) );
        west.setWidth( 250 );
        west.setMinWidth( 200 );
        p.getRoot().insertChild( Position.WEST, west );

        final PanelDefinition east = new PanelDefinitionImpl();
        east.addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "TodoListScreen" ) ) );
        east.setWidth( 300 );
        east.setMinWidth( 200 );
        p.getRoot().insertChild( Position.EAST, east );

        p.getRoot().addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "welcome" ) ) );
        p.getRoot().setMinHeight( 100 );

        final PanelDefinition south = new PanelDefinitionImpl();
        south.addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "YouTubeScreen" ) ) );
        south.setHeight( 400 );
        p.getRoot().insertChild( Position.SOUTH, south );

        return p;
    }
}
