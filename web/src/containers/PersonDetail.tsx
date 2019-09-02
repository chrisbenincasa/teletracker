import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators, Dispatch } from 'redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router';
import { connect } from 'react-redux';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import { Person } from '../types';
import * as R from 'ramda';
import {
  Chip,
  Grid,
  LinearProgress,
  CardMedia,
  createStyles,
  Hidden,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { layoutStyles } from '../styles';
import { ResponsiveImage } from '../components/ResponsiveImage';
import AddToListDialog from '../components/AddToListDialog';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import { Thing } from '../types';
import withUser, { WithUserProps } from '../components/withUser';
import ItemCard from '../components/ItemCard';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
      background:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
      //To do: integrate with theme styling for primary
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: 10,
    },
    genre: { margin: 5 },
    genreContainer: { display: 'flex', flexWrap: 'wrap' },
    itemInformationContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
        marginLeft: 20,
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
    },
    itemDetailContainer: {
      margin: 20,
      display: 'flex',
      flex: '1 1 auto',
      color: '#fff',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
      },
    },
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
    },
  });

interface OwnProps {}

interface StateProps {
  isAuthed: boolean;
  person?: Person;
}

interface DispatchProps {
  personFetchInitiated: (id: PersonFetchInitiatedPayload) => void;
}

interface RouteProps {
  id: string;
}

type NotOwnProps = DispatchProps &
  RouteComponentProps<RouteProps> &
  WithStyles<typeof styles> &
  WithUserProps;

type Props = OwnProps & StateProps & NotOwnProps;

class PersonDetail extends React.Component<Props> {
  componentDidMount() {
    this.props.personFetchInitiated({ id: this.props.match.params.id });

    // const { person } = this.props;
    // const { mainItemIndex } = this.state;

    // // Grab random item from filtered list of popular movies
    // if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
    //   const highestRated = popular.filter(item => {
    //     const thing = thingsBySlug[item];
    //     const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
    //     const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
    //     return voteAverage > 7 && voteCount > 1000;
    //   });

    //   const randomItem = Math.floor(Math.random() * highestRated.length);
    //   const popularItem = popular.findIndex(
    //     name => name === highestRated[randomItem],
    //   );

    //   this.setState({
    //     mainItemIndex: popularItem,
    //   });
    // }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderTitle = (thing: Thing) => {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          width: '80%',
          marginBottom: 10,
        }}
      >
        <Typography color="inherit" variant="h4">
          {`${thing.name}`}
        </Typography>
      </div>
    );
  };

  renderFilmography = () => {
    const { classes, person, userSelf } = this.props;

    let filmography =
      (person &&
        person.metadata &&
        person.metadata['combined_credits'] &&
        person.metadata['combined_credits'].cast) ||
      [];
    filmography = filmography.sort((a, b) =>
      a.popularity < b.popularity ? 1 : -1,
    );
    let genres = filmography
      .map(genre => genre.genre_ids)
      .flat()
      .reduce((accumulator, currentValue) => {
        if (accumulator.indexOf(currentValue) === -1) {
          accumulator.push(currentValue);
        }
        return accumulator;
      }, []);
    console.log(genres);
    return (
      <div className={classes.genreContainer}>
        <Typography
          color="inherit"
          variant="h5"
          style={{ display: 'block', width: '100%' }}
        >
          Filmography
        </Typography>
        <Chip
          key={0}
          label="All"
          className={classes.genre}
          onClick={() => {}}
          color="secondary"
        />
        {genres.map(genre => (
          <Chip
            key={genre}
            label={genre}
            className={classes.genre}
            onClick={() => {}}
          />
        ))}
        <Grid container spacing={2}>
          {filmography.map(item =>
            item && item.poster_path ? (
              <ItemCard
                key={item.id}
                userSelf={userSelf}
                item={item}
                itemCardVisible={false}
                // addButton
              />
            ) : null,
          )}
        </Grid>
      </div>
    );
  };

  renderDescriptiveDetails = (thing: Thing) => {
    const { classes } = this.props;
    const { metadata } = thing;
    const biography = (metadata && metadata['biography']) || '';

    return (
      <div className={classes.descriptionContainer}>
        <div
          style={{
            display: 'flex',
            marginBottom: 8,
            flexDirection: 'column',
            alignItems: 'self-start',
            color: '#fff',
          }}
        >
          <Hidden only={['xs', 'sm']}>{this.renderTitle(thing)}</Hidden>
        </div>
        <div>
          <Typography color="inherit">{biography}</Typography>
        </div>
      </div>
    );
  };

  renderPerson() {
    let { classes, person } = this.props;

    const backdrop =
      (person &&
        person.metadata &&
        person.metadata['combined_credits'] &&
        person.metadata['combined_credits'].cast[0]) ||
      {};
    console.log(backdrop);

    console.log(person);
    return !person ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <div className={classes.backdrop}>
          <ResponsiveImage
            item={backdrop}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              objectPosition: 'center top',
              width: '100%',
              height: '100%',
            }}
            pictureStyle={{
              position: 'absolute',
              width: '100%',
              height: 'auto',
              opacity: 0.2,
              filter: 'blur(3px)',
            }}
          />
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          />
          <div className={classes.itemDetailContainer}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
            >
              <Hidden smUp>{this.renderTitle(person)}</Hidden>
              <div className={classes.posterContainer}>
                <CardMedia
                  src={imagePlaceholder}
                  item={person}
                  component={ResponsiveImage}
                  imageType="profile"
                  imageStyle={{
                    width: '100%',
                  }}
                />
              </div>
              {/*
              {this.renderWatchedToggle()}
              {this.renderTrackingToggle()} */}
              {/* <AddToListDialog
                open={manageTrackingModalOpen}
                onClose={this.closeManageTrackingModal.bind(this)}
                userSelf={userSelf!}
                item={itemDetail}
              /> */}
            </div>
            <div className={classes.itemInformationContainer}>
              {this.renderDescriptiveDetails(person)}
              {this.renderFilmography()}
            </div>
          </div>
        </div>
      </React.Fragment>
    );
  }

  render() {
    let { isAuthed } = this.props;

    return isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPerson()}</div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => StateProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    // isFetching: appState.itemDetail.fetching,
    person:
      appState.people.peopleById[props.match.params.id] ||
      appState.people.peopleBySlug[props.match.params.id],
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch =>
  bindActionCreators(
    {
      personFetchInitiated,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(PersonDetail),
    ),
  ),
);
