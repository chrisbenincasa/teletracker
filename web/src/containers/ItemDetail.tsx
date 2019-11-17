import {
  Backdrop,
  Button,
  CardMedia,
  Chip,
  createStyles,
  Dialog,
  Fade,
  Hidden,
  IconButton,
  LinearProgress,
  Modal,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { fade } from '@material-ui/core/styles/colorManipulator';
import { ChevronLeft, PlayArrow } from '@material-ui/icons';
import { Rating } from '@material-ui/lab';
import _ from 'lodash';
import * as R from 'ramda';
import React, { useState } from 'react';
import ReactGA from 'react-ga';
import { Helmet } from 'react-helmet';
import { connect } from 'react-redux';
import {
  RouteComponentProps,
  useHistory,
  useLocation,
  useParams,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  itemFetchInitiated,
  ItemFetchInitiatedPayload,
} from '../actions/item-detail';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import ThingAvailability from '../components/Availability';
import Cast from '../components/Cast';
import ManageTracking from '../components/ManageTracking';
import MarkAsWatched from '../components/MarkAsWatched';
import Recommendations from '../components/Recommendations';
import { ResponsiveImage } from '../components/ResponsiveImage';
import RouterLink from '../components/RouterLink';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Genre } from '../types';
import { Item } from '../types/v2/Item';
import {
  formatRuntime,
  getVoteAverage,
  getVoteCount,
} from '../utils/textHelper';
import Login from './Login';
import moment from 'moment';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
      //To do: integrate with theme styling for primary
      position: 'relative',
    },
    backdropContainer: {
      height: 'auto',
      overflow: 'hidden',
      position: 'absolute',
      top: 0,
      width: '100%',
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundColor: 'rgba(48, 48, 48, 0.5)',
      backgroundImage:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
    },
    badge: {
      margin: theme.spacing(1),
    },
    card: {
      margin: '10px 0',
    },
    carousel: {
      height: 220,
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: 10,
    },
    genre: {
      margin: 5,
      cursor: 'pointer',
    },
    genreContainer: {
      display: 'flex',
      flexWrap: 'wrap',
    },
    heroContent: {
      maxWidth: 600,
      margin: '0 auto',
      padding: `${theme.spacing(8)}px 0 ${theme.spacing(7)}px`,
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
    itemInformationContainer: {
      [theme.breakpoints.up('sm')]: {
        marginLeft: 20,
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
    },
    leftContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      [theme.breakpoints.up('md')]: {
        position: 'sticky',
        top: 75,
        height: 475,
      },
    },
    modal: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
      width: '50%',
      display: 'flex',
      flex: '0 1 auto',
      position: 'relative',
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
    },
    root: {
      flexGrow: 1,
    },
    seasonContainer: {
      display: 'flex',
      flexDirection: 'column',
    },
    seasonPoster: {
      boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
      width: 100,
    },
    seasonTitle: {
      marginLeft: 8,
    },
    titleContainer: {
      display: 'flex',
      marginBottom: 8,
      flexDirection: 'column',
      alignItems: 'self-start',
      color: '#fff',
    },
    trailerVideo: {
      width: '60vw',
      height: '34vw',
      [theme.breakpoints.down('sm')]: {
        width: '100vw',
        height: '56vw',
      },
    },
  });

interface OwnProps {
  isAuthed: boolean;
  isFetching: boolean;
  itemDetail?: Item;
  genres?: Genre[];
}

interface DispatchProps {
  fetchItemDetails: (payload: ItemFetchInitiatedPayload) => void;
}

interface RouteParams {
  id: string;
  type: string;
}

type NotOwnProps = RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

type Props = OwnProps & NotOwnProps;

function ItemDetails(props: Props) {
  const [showPlayIcon, setShowPlayIcon] = useState<boolean>(false);
  const [trailerModalOpen, setTrailerModalOpen] = useState<boolean>(false);
  const [loginModalOpen, setLoginModalOpen] = useState<boolean>(false);

  let location = useLocation();
  let history = useHistory();
  let params = useParams();

  React.useEffect(() => {
    const { isLoggedIn, userSelf } = props;

    loadItem();

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }, [location]);

  const loadItem = () => {
    let itemId = params.id;
    let itemType = params.type;

    props.fetchItemDetails({ id: itemId, type: itemType });
  };

  const setPlayTrailerIcon = () => {
    setShowPlayIcon(!showPlayIcon);
  };

  const setTrailerModal = () => {
    setTrailerModalOpen(!trailerModalOpen);
  };

  const closeLoginModal = () => {
    setLoginModalOpen(false);
  };

  const renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  const renderTitle = (Item: Item) => {
    const title = Item.original_title;
    const voteAverage = getVoteAverage(Item);
    const voteCount = getVoteCount(Item);
    const runtime =
      (Item.runtime && formatRuntime(Item.runtime, Item.type)) || null;

    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          width: '100%',
          marginBottom: 10,
          zIndex: 99999,
        }}
      >
        <Typography color="inherit" variant="h4" itemProp="name">
          {`${title} (${moment(Item.release_date).format('YYYY')})`}
        </Typography>
        <div style={{ display: 'flex', flexDirection: 'row' }}>
          <Rating value={voteAverage} precision={0.1} readOnly />
          <Typography
            color="inherit"
            variant="body1"
            style={{ marginRight: 10 }}
          >
            {`(${voteCount})`}
          </Typography>
        </div>
        <Typography color="inherit" variant="body1" itemProp="duration">
          {runtime}
        </Typography>
      </div>
    );
  };

  const renderDescriptiveDetails = (Item: Item) => {
    const { classes, genres } = props;
    const ItemGenres = (Item.genres || []).map(g => g.id);
    const overview = Item.overview || '';

    const genresToRender = _.filter(genres || [], genre => {
      return _.includes(ItemGenres, genre.id);
    });

    return (
      <div className={classes.descriptionContainer}>
        <div className={classes.titleContainer}>
          <Hidden smDown>{renderTitle(Item)}</Hidden>
        </div>
        <div>
          <Typography color="inherit" itemProp="about">
            {overview}
          </Typography>
        </div>
        <div className={classes.genreContainer}>
          {genresToRender &&
            genresToRender.length &&
            genresToRender.map(genre => (
              <Chip
                key={genre.id}
                label={genre.name}
                className={classes.genre}
                component={RouterLink}
                to={`/popular?genres=${genre.id}`}
                itemProp="genre"
                clickable
              />
            ))}
        </div>
      </div>
    );
  };

  // TODO: index seasons
  // renderSeriesDetails = (thing: Item) => {
  //   const { classes } = this.props;
  //   let seasons = getMetadataPath(thing, 'seasons');
  //   seasons =
  //     seasons &&
  //     seasons.filter(
  //       season =>
  //         season.episode_count > 0 &&
  //         season.poster_path &&
  //         season.name !== 'Specials',
  //     );
  //   const Season = ({ index, style }) => (
  //     <div
  //       className={classes.seasonContainer}
  //       style={style}
  //       key={seasons[index].id}
  //     >
  //       <Badge
  //         className={classes.badge}
  //         badgeContent={seasons[index].episode_count}
  //         color="primary"
  //       >
  //         <img
  //           src={`https://image.tmdb.org/t/p/w342/${
  //             seasons[index].poster_path
  //           }`}
  //           className={classes.seasonPoster}
  //         />
  //       </Badge>
  //       <Typography style={{ marginLeft: 8 }}>{seasons[index].name}</Typography>
  //     </div>
  //   );

  //   return seasons && seasons.length > 0 ? (
  //     <React.Fragment>
  //       <Typography
  //         color="inherit"
  //         variant="h5"
  //         className={classes.seasonTitle}
  //       >
  //         Seasons
  //       </Typography>

  //       <div className={classes.carousel}>
  //         <AutoSizer>
  //           {({ height, width }) => (
  //             <LazyList
  //               height={220}
  //               itemCount={seasons.length}
  //               itemSize={125}
  //               layout="horizontal"
  //               width={width}
  //               style={{ overflowX: 'auto', overflowY: 'hidden' }}
  //             >
  //               {Season}
  //             </LazyList>
  //           )}
  //         </AutoSizer>
  //       </div>
  //     </React.Fragment>
  //   ) : null;
  // };

  const renderItemDetails = () => {
    let { classes, isFetching, itemDetail, userSelf } = props;
    let itemType;

    if (itemDetail && itemDetail.type && itemDetail.type === 'movie') {
      itemType = 'Movie';
    } else if (itemDetail && itemDetail.type && itemDetail.type === 'show') {
      itemType = 'TVSeries';
    }

    return isFetching || !itemDetail ? (
      renderLoading()
    ) : (
      <React.Fragment>
        <Helmet>
          <title>{`${itemDetail.original_title} | Teletracker`}</title>
          <meta
            name="title"
            property="og:title"
            content={`${itemDetail.original_title} | Where to stream, rent, or buy. Track it today!`}
          />
          <meta
            name="description"
            property="og:description"
            content={`Find out where to stream, rent, or buy ${itemDetail.original_title} online. Track it to find out when it's available on one of your services.`}
          />
          {/* TODO FIX <meta
            name="image"
            property="og:image"
            content={`https://image.tmdb.org/t/p/w342${itemDetail.posterPath}`}
          /> */}
          <meta property="og:type" content="video.movie" />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content="342" />
          <meta
            data-react-helmet="true"
            property="og:image:height"
            content="513"
          />
          <meta
            property="og:url"
            content={`http://teletracker.com${location.pathname}`}
          />
          <meta
            data-react-helmet="true"
            name="twitter:card"
            content="summary"
          />
          <meta
            name="twitter:title"
            content={`${itemDetail.original_title} - Where to Stream, Rent, or Buy It Online`}
          />
          <meta
            name="twitter:description"
            content={`Find out where to stream, rent, or buy ${itemDetail.original_title} online. Track it to find out when it's available on one of your services.`}
          />

          {/* TODO FIX <meta
            name="twitter:image"
            content={`https://image.tmdb.org/t/p/w342${itemDetail.posterPath}`}
          /> */}
          <meta
            name="keywords"
            content={`${itemDetail.original_title}, ${itemDetail.type}, stream, streaming, rent, buy, watch, track`}
          />
          <link
            rel="canonical"
            href={`http://teletracker.com${location.pathname}`}
          />
        </Helmet>

        <div className={classes.backdrop}>
          <div className={classes.backdropContainer}>
            <ResponsiveImage
              item={itemDetail}
              imageType="backdrop"
              imageStyle={{
                objectFit: 'cover',
                objectPosition: 'center top',
                width: '100%',
                height: '100%',
              }}
              pictureStyle={{
                display: 'block',
                position: 'relative',
                height: 'auto',
                filter: 'blur(3px)',
              }}
            />
            <div className={classes.backdropGradient} />
          </div>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
            }}
          >
            <Button
              size="small"
              onClick={history.goBack}
              variant="contained"
              aria-label="Go Back"
              style={{ marginTop: 20, marginLeft: 20 }}
              startIcon={<ChevronLeft style={{ marginRight: 8 }} />}
            >
              Go Back
            </Button>

            <div
              className={classes.itemDetailContainer}
              itemScope
              itemType={`http://schema.org/${itemType}`}
            >
              <div className={classes.leftContainer}>
                <Hidden mdUp>{renderTitle(itemDetail)}</Hidden>
                <div
                  className={classes.posterContainer}
                  // This is causing an issue with Cast re-endering on Enter/Leave
                  // TODO: Investigate
                  // onMouseEnter={setPlayTrailerIcon}
                  // onMouseLeave={setPlayTrailerIcon}
                >
                  {showPlayIcon &&
                  itemDetail.id === '7b6dbeb1-8353-45a7-8c9b-7f9ab8b037f8' ? (
                    <IconButton
                      aria-haspopup="true"
                      color="inherit"
                      style={{ position: 'absolute' }}
                      onClick={setTrailerModal}
                    >
                      <PlayArrow fontSize="large" />
                    </IconButton>
                  ) : null}
                  <CardMedia
                    src={imagePlaceholder}
                    item={itemDetail}
                    component={ResponsiveImage}
                    imageType="poster"
                    imageStyle={{
                      width: '100%',
                      boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                    }}
                  />
                </div>

                <MarkAsWatched itemDetail={itemDetail} />
                <ManageTracking itemDetail={itemDetail} />
              </div>
              <div className={classes.itemInformationContainer}>
                {renderDescriptiveDetails(itemDetail)}
                <div>
                  <div style={{ marginTop: 10 }}>
                    <ThingAvailability
                      userSelf={userSelf}
                      itemDetail={itemDetail}
                    />
                  </div>
                </div>
                <Cast itemDetail={itemDetail} />
                {/* {renderSeriesDetails(itemDetail)} */}
                <Recommendations itemDetail={itemDetail} userSelf={userSelf} />
              </div>
            </div>
          </div>
        </div>
        <Dialog
          open={loginModalOpen}
          onClose={closeLoginModal}
          closeAfterTransition
          BackdropComponent={Backdrop}
          BackdropProps={{
            timeout: 500,
          }}
        >
          <Login />
        </Dialog>
        <Modal
          aria-labelledby="transition-modal-title"
          aria-describedby="transition-modal-description"
          className={classes.modal}
          open={trailerModalOpen}
          onClose={setTrailerModal}
          closeAfterTransition
          BackdropComponent={Backdrop}
          BackdropProps={{
            timeout: 500,
          }}
          style={{ backgroundColor: 'rgba(0, 0, 0, 0.8)' }}
        >
          <Fade in={trailerModalOpen}>
            <iframe
              width="600"
              height="338"
              src="https://www.youtube.com/embed/m8e-FF8MsqU?autoplay=1 "
              frameBorder="0"
              allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              className={classes.trailerVideo}
            />
          </Fade>
        </Modal>
      </React.Fragment>
    );
  };

  return (
    <div style={{ display: 'flex', flexGrow: 1 }}>{renderItemDetails()}</div>
  );
}

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => OwnProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isFetching: appState.itemDetail.fetching,
    itemDetail: appState.itemDetail.itemDetail,
    genres: appState.metadata.genres,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      fetchItemDetails: itemFetchInitiated,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(ItemDetails)),
);
