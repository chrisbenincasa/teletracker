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
import {
  ChevronLeft,
  ExpandLess,
  ExpandMore,
  Lens,
  PlayArrow,
} from '@material-ui/icons';
import { Rating } from '@material-ui/lab';
import _ from 'lodash';
import moment from 'moment';
import { useRouter } from 'next/router';
import * as R from 'ramda';
import React, { useState } from 'react';
import ReactGA from 'react-ga';
import { connect } from 'react-redux';
import { RouteComponentProps } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  itemFetchInitiated,
  ItemFetchInitiatedPayload,
  itemPrefetchSuccess,
} from '../actions/item-detail';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import ThingAvailability from '../components/Availability';
import MarkAsWatched from '../components/Buttons/MarkAsWatched';
import Cast from '../components/Cast';
import ManageTracking from '../components/ManageTracking';
import Recommendations from '../components/Recommendations';
import { ResponsiveImage } from '../components/ResponsiveImage';
import withUser, { WithUserProps } from '../components/withUser';
import { useWidth } from '../hooks/useWidth';
import { AppState } from '../reducers';
import { Genre } from '../types';
import { Item } from '../types/v2/Item';
import {
  formatRuntime,
  getVoteAverage,
  getVoteCountFormatted,
} from '../utils/textHelper';
import Login from './Login';
import Link from 'next/link';
import { extractItem } from '../utils/item-utils';

const styles = (theme: Theme) =>
  createStyles({
    actionButtonContainer: {
      display: 'flex',
      flexDirection: 'column',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'row',
      },
      width: '100%',
    },
    actionButton: {
      marginTop: theme.spacing(1),
      width: '100%',
      [theme.breakpoints.down('sm')]: {
        margin: theme.spacing(1, 0.5),
      },
    },
    backdrop: {
      width: '100%',
      height: '100%',
      position: 'relative',
    },
    backdropContainer: {
      height: 'auto',
      overflow: 'hidden',
      top: 0,
      width: '100%',
      position: 'fixed',
      [theme.breakpoints.down('sm')]: {
        height: '100%',
      },
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundColor: theme.custom.backdrop.backgroundColor,
      backgroundImage: theme.custom.backdrop.backgroundImage,
    },
    badge: {
      margin: theme.spacing(1),
    },
    carousel: {
      height: 220,
    },
    genre: {
      margin: theme.spacing(1, 1, 1, 0),
      cursor: 'pointer',
      [theme.breakpoints.down('sm')]: {
        display: 'flex',
        flexGrow: 1,
      },
    },
    genreContainer: {
      display: 'flex',
      flexWrap: 'wrap',
      [theme.breakpoints.down('sm')]: {
        justifyContent: 'center',
      },
    },
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
    },
    information: {
      display: 'flex',
      [theme.breakpoints.down('sm')]: {
        textAlign: 'center',
      },
    },
    informationContainer: {
      [theme.breakpoints.down('sm')]: {
        marginTop: theme.spacing(1),
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      },
    },
    itemDetailContainer: {
      position: 'relative',
      padding: theme.spacing(3),
      display: 'flex',
      flex: '1 1 auto',
      color: theme.palette.primary.contrastText,
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
        padding: theme.spacing(1),
      },
      width: '100%',
    },
    itemInformationContainer: {
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: theme.palette.primary.contrastText,
      flexDirection: 'column',
      position: 'relative',
      marginBottom: theme.spacing(2),
      [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing(3),
      },
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
      margin: '0 auto',
      width: '50%',
      position: 'relative',
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
    },
    rating: {
      borderRadius: 0,
      height: 'auto',
    },
    seasonContainer: {
      display: 'flex',
      flexDirection: 'column',
    },
    seasonPoster: {
      boxShadow: theme.shadows[1],
      width: 100,
    },
    seasonTitle: {
      marginLeft: theme.spacing(1),
    },
    separator: {
      display: 'flex',
      alignSelf: 'center',
      fontSize: 8,
      margin: theme.spacing(0, 1),
    },
    titleContainer: {
      marginBottom: theme.spacing(1),
      color: theme.palette.primary.contrastText,
    },
    title: {
      [theme.breakpoints.up('sm')]: {
        lineHeight: 0.85,
      },
    },
    titleWrapper: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',

      [theme.breakpoints.down('sm')]: {
        alignItems: 'center',
        textAlign: 'center',
        margin: theme.spacing(1, 0, 2, 0),
        lineHeight: 1,
      },
      width: '100%',
      marginBottom: theme.spacing(1),
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
  initialItem?: Item;
  genres?: Genre[];
  itemsById: { [key: string]: Item };
}

interface DispatchProps {
  fetchItemDetails: (payload: ItemFetchInitiatedPayload) => void;
  itemPrefetchSuccess: (payload: Item) => void;
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
  const [showFullOverview, setshowFullOverview] = useState<boolean>(false);
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);
  let nextRouter = useRouter();

  React.useEffect(() => {
    const { isLoggedIn, userSelf } = props;

    loadItem();
    ReactGA.pageview(nextRouter.asPath);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }, [nextRouter.query]);

  const loadItem = () => {
    if (props.initialItem) {
      props.itemPrefetchSuccess(props.initialItem);
    } else {
      let itemId = nextRouter.query.id as string;

      // TODO: This is an optimization, but sometimes items don't always have all of the
      // necessary information loaded when triggered from other pages. We should figure out
      // a way to determine if the full item needs to be fetched, so we can still cache
      // previous results.
      // if (props.itemsById[itemId]) {
      //   props.itemPrefetchSuccess(props.itemsById[itemId]);
      // } else if (props.itemsBySlug[itemId]) {
      //   props.itemPrefetchSuccess(props.itemsBySlug[itemId]);
      // } else {
      // }
      let itemType = nextRouter.pathname
        .split('/')
        .filter(s => s.length > 0)[0];

      props.fetchItemDetails({ id: itemId, type: itemType });
    }
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

  const renderTitle = (item: Item) => {
    const { classes } = props;

    return (
      <div className={classes.titleWrapper}>
        <Typography
          color="inherit"
          variant="h2"
          itemProp="name"
          className={classes.title}
        >
          {item.canonicalTitle}
        </Typography>
      </div>
    );
  };

  const renderInformation = (item: Item) => {
    const { classes } = props;
    const voteAverage = getVoteAverage(item);
    const voteCount = getVoteCountFormatted(item);
    const runtime =
      (item.runtime && formatRuntime(item.runtime, item.type)) || '';
    const releaseDate =
      (item.release_date && moment(item.release_date).format('YYYY')) || '';
    const ratingObject = _.filter(
      item.release_dates,
      item => item.country_code === 'US',
    );
    const rating = ratingObject[0]?.certification;

    return (
      <div className={classes.informationContainer}>
        <div style={{ display: 'flex', flexDirection: 'row' }}>
          <Rating value={voteAverage} precision={0.1} readOnly />
          <Typography color="inherit" variant="body1">
            {`(${voteCount})`}
          </Typography>
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
          }}
        >
          <Typography
            color="inherit"
            variant="body1"
            itemProp="duration"
            className={classes.information}
          >
            {runtime}
            {runtime && releaseDate && <Lens className={classes.separator} />}
            {releaseDate}
            {rating && <Lens className={classes.separator} />}
            {rating && `Rated ${rating}`}
          </Typography>
        </div>
      </div>
    );
  };

  const renderGenres = (item: Item) => {
    const { classes, genres } = props;
    const itemGenres = (item.genres || []).map(g => g.id);
    const genresToRender = _.filter(genres || [], genre => {
      return _.includes(itemGenres, genre.id);
    });
    return (
      <div className={classes.genreContainer}>
        {genresToRender &&
          genresToRender.length > 0 &&
          genresToRender.map(genre => (
            <Link key={genre.id} href={`/popular?genres=${genre.id}`} passHref>
              <Chip
                label={genre.name}
                className={classes.genre}
                component="a"
                itemProp="genre"
                clickable
              />
            </Link>
          ))}
      </div>
    );
  };

  const renderDescriptiveDetails = (item: Item) => {
    const { classes } = props;

    return (
      <div className={classes.titleContainer}>
        <Hidden smDown>{renderTitle(item)}</Hidden>
        <Hidden smDown>{renderInformation(item)}</Hidden>
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
    const overview = itemDetail?.overview || '';
    const isMobile = ['xs', 'sm'].includes(width);
    const truncateSize = isMobile ? 300 : 900;

    const truncatedOverview = showFullOverview
      ? overview
      : overview.substr(0, truncateSize);
    const formattedOverview = truncatedOverview
      .split('\n')
      .filter(s => s.length > 0)
      .map((part, idx) => (
        <React.Fragment key={idx}>
          <Typography color="inherit">{part}</Typography>
          <br />
        </React.Fragment>
      ));

    if (itemDetail?.type === 'movie') {
      itemType = 'Movie';
    } else if (itemDetail && itemDetail.type && itemDetail.type === 'show') {
      itemType = 'TVSeries';
    }

    return isFetching || !itemDetail ? (
      renderLoading()
    ) : (
      <React.Fragment>
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
                pointerEvents: 'none', // Disables ios preview on tap & hold
              }}
              pictureStyle={{
                display: 'block',
                position: 'relative',
                height: '100vh',
              }}
            />
            <div className={classes.backdropGradient} />
          </div>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'flex-start',
              flexGrow: 1,
            }}
          >
            {!isMobile && (
              <Button
                size="small"
                onClick={nextRouter.back}
                variant="contained"
                aria-label="Go Back"
                style={{ marginTop: 20, marginLeft: 20 }}
                startIcon={<ChevronLeft />}
              >
                Go Back
              </Button>
            )}

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
                <Hidden mdUp>{renderInformation(itemDetail)}</Hidden>
                <Hidden mdUp>{renderGenres(itemDetail)}</Hidden>
                <div className={classes.actionButtonContainer}>
                  <div className={classes.actionButton}>
                    <MarkAsWatched
                      itemDetail={itemDetail}
                      className={classes.actionButton}
                    />
                  </div>
                  <div className={classes.actionButton}>
                    <ManageTracking
                      itemDetail={itemDetail}
                      className={classes.actionButton}
                    />
                  </div>
                </div>
              </div>
              <div className={classes.itemInformationContainer}>
                {renderDescriptiveDetails(itemDetail)}
                <Hidden smDown>{renderGenres(itemDetail)}</Hidden>
                <ThingAvailability
                  userSelf={userSelf}
                  itemDetail={itemDetail}
                />
                <div>
                  <Typography
                    color="inherit"
                    variant="h5"
                    className={classes.header}
                  >
                    Description
                  </Typography>
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <React.Fragment>{formattedOverview}</React.Fragment>
                    {overview.length > truncateSize ? (
                      <Button
                        size="small"
                        variant="contained"
                        aria-label={
                          showFullOverview ? 'Read Less' : 'Read More'
                        }
                        onClick={() => setshowFullOverview(!showFullOverview)}
                        style={{
                          marginTop: 5,
                          display: 'flex',
                          alignSelf: 'center',
                        }}
                      >
                        {showFullOverview ? (
                          <ExpandLess style={{ marginRight: 8 }} />
                        ) : (
                          <ExpandMore style={{ marginRight: 8 }} />
                        )}
                        {showFullOverview ? 'Read Less' : 'Read More'}
                      </Button>
                    ) : null}
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
    itemDetail: appState.itemDetail.itemDetail
      ? extractItem(
          appState.itemDetail.itemDetail,
          undefined,
          appState.itemDetail.thingsById,
        )
      : undefined,
    itemsById: appState.itemDetail.thingsById,
    genres: appState.metadata.genres,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      fetchItemDetails: itemFetchInitiated,
      itemPrefetchSuccess: itemPrefetchSuccess,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(ItemDetails)),
);
