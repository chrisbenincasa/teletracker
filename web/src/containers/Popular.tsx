import {
  CardMedia,
  createStyles,
  Fab,
  Fade,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import { fade } from '@material-ui/core/styles/colorManipulator';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link as RouterLink } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import * as R from 'ramda';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { retrievePopular } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { ResponsiveImage } from '../components/ResponsiveImage';
import AddToListDialog from '../components/AddToListDialog';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 230,
      },
      width: '80%',
      display: 'flex',
      flex: '0 1 auto',
      flexDirection: 'column',
      position: 'absolute',
      top: 20,
      left: 20,
    },
    posterImage: {
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
    },
    title: {
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      marginBottom: 10,
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      position: 'absolute',
      bottom: 0,
      right: 10,
      marginBottom: 10,
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
    itemCTA: {
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
      width: '100%',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  popular?: string[];
  thingsBySlug: { [key: string]: Thing };
}

interface DispatchProps {
  retrievePopular: () => any;
}

type Props = OwnProps & InjectedProps & DispatchProps & WithUserProps;

interface State {
  mainItemIndex: number;
  manageTrackingModalOpen: boolean;
}

class Popular extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
    manageTrackingModalOpen: false,
  };

  componentDidMount() {
    this.props.retrievePopular();
  }

  componentDidUpdate(prevProps) {
    const { popular, thingsBySlug } = this.props;
    const { mainItemIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
      const highestRated = popular.filter(item => {
        const thing = thingsBySlug[item];
        const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
        const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
        return voteAverage > 7 && voteCount > 1000;
      });

      const randomItem = Math.floor(Math.random() * highestRated.length);
      const popularItem = popular.findIndex(
        name => name === highestRated[randomItem],
      );

      this.setState({
        mainItemIndex: popularItem,
      });
    }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderTitle = (thing: Thing) => {
    const { classes } = this.props;
    const title = thing.name || '';

    return (
      <div className={classes.titleContainer} style={{}}>
        <Typography color="inherit" variant="h3">
          {`${title}`}
        </Typography>
      </div>
    );
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    const trackingCTA = 'Add to List';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.openManageTrackingModal}
          style={{ marginTop: 5, width: '100%' }}
        >
          <ListIcon style={{ marginRight: 8 }} />
          {trackingCTA}
        </Fab>
      </div>
    );
  };

  renderMainPopularItem = () => {
    let { classes, popular, thingsBySlug, userSelf } = this.props;
    const { mainItemIndex, manageTrackingModalOpen } = this.state;
    popular = popular || [];
    const thing = thingsBySlug[popular[mainItemIndex]];

    return thing ? (
      <Fade in={true}>
        <div style={{ position: 'relative' }}>
          <ResponsiveImage
            item={thing}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              width: '100%',
              height: '100%',
              maxHeight: 424,
            }}
            pictureStyle={{
              display: 'block',
            }}
          />
          <div className={classes.posterContainer}>
            <RouterLink
              to={'/' + thing.type + '/' + thing.normalizedName}
              style={{
                display: 'block',
                height: '100%',
                textDecoration: 'none',
              }}
            >
              <CardMedia
                src={imagePlaceholder}
                item={thing}
                component={ResponsiveImage}
                imageType="poster"
                imageStyle={{
                  width: '100%',
                  boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                }}
                pictureStyle={{
                  display: 'block',
                }}
              />
            </RouterLink>

            {this.renderTrackingToggle()}
            <AddToListDialog
              open={manageTrackingModalOpen}
              onClose={this.closeManageTrackingModal.bind(this)}
              userSelf={userSelf!}
              item={thing}
            />
          </div>
          {this.renderTitle(thing)}
        </div>
      </Fade>
    ) : null;
  };

  renderPopular = () => {
    const { popular, userSelf, thingsBySlug } = this.props;

    return popular && popular && popular.length ? (
      <div style={{ padding: 8, margin: 20 }}>
        <Typography color="inherit" variant="h4" style={{ marginBottom: 10 }}>
          Popular Movies
        </Typography>
        <Grid container spacing={2}>
          {popular.map((result, index) => {
            let thing = thingsBySlug[result];
            if (thing && index !== this.state.mainItemIndex) {
              return <ItemCard key={result} userSelf={userSelf} item={thing} />;
            } else {
              return null;
            }
          })}
        </Grid>
      </div>
    ) : null;
  };

  render() {
    return this.props.popular ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        {this.renderMainPopularItem()}
        {this.renderPopular()}
      </div>
    ) : (
      this.renderLoading()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    popular: appState.popular.popular,
    thingsBySlug: appState.itemDetail.thingsBySlug,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Popular),
  ),
);
