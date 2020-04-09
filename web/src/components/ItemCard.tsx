import React, { useEffect, useRef, useState, RefObject } from 'react';
import {
  Button,
  Card,
  CardMedia,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Fade,
  Grid,
  IconButton,
  makeStyles,
  Theme,
  Tooltip,
  Typography,
  Zoom,
} from '@material-ui/core';
import { green, red } from '@material-ui/core/colors';
import { GridProps } from '@material-ui/core/Grid';
import {
  Check,
  Close,
  Delete as DeleteIcon,
  PlaylistAdd,
  ThumbDown,
  ThumbUp,
} from '@material-ui/icons';
import { connect } from 'react-redux';
import RouterLink from 'next/link';
import { bindActionCreators, Dispatch } from 'redux';
import { ACTION_ENJOYED, ACTION_WATCHED } from '../actions/item-detail';
import {
  ListTrackingUpdatedInitiatedPayload,
  updateListTracking,
} from '../actions/lists';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import { GRID_COLUMNS } from '../constants/';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import { UserSelf } from '../reducers/user';
import { ActionType, List } from '../types';
import HasImagery from '../types/HasImagery';
import { Linkable, ThingLikeStruct } from '../types/Thing';
import AddToListDialog from './Dialogs/AddToListDialog';
import { ResponsiveImage } from './ResponsiveImage';
import { Item, itemHasTag } from '../types/v2/Item';
import useIntersectionObserver from '../hooks/useIntersectionObserver';
import { useWidth } from '../hooks/useWidth';
import { hexToRGB } from '../utils/style-utils';

const useStyles = makeStyles((theme: Theme) => ({
  title: {
    flex: 1,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  cardContent: {
    flexGrow: 1,
  },
  cardHoverEnter: {
    overflow: 'hidden',
    width: '100%',
    height: '100%',
    display: 'block',
  },
  cardHoverExit: {
    display: 'block',
    width: '100%',
    height: '100%',
    opacity: 1,
    overflow: 'hidden',
  },
  hoverActions: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    position: 'absolute',
    top: 0,
    left: 0,
    background: hexToRGB(theme.palette.grey[900], 0.85),
    zIndex: theme.zIndex.appBar - 1,
  },
  hoverDelete: {
    color: theme.palette.common.white,
    '&:hover': {
      color: red[300],
    },
  },
  hoverWatch: {
    color: theme.palette.common.white,
    '&:hover': {
      color: green[300],
    },
  },
  hoverWatchInvert: {
    color: green[300],
    '&:hover': {
      color: theme.palette.common.white,
    },
  },
  hoverRatingThumbsDown: {
    color: theme.palette.common.white,
    '&:hover': {
      color: red[300],
    },
  },
  hoverRatingThumbsUp: {
    color: theme.palette.common.white,
    '&:hover': {
      color: green[300],
    },
  },
  ratingHover: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-around',
    flex: '1 0 auto',
    alignItems: 'center',
    overflow: 'hidden',
    position: 'absolute',
    top: 0,
    zIndex: 1,
    height: '100%',
    width: '100%',
    backgroundColor: hexToRGB(theme.palette.grey[900], 0.85),
  },
  ratingTitle: {
    color: theme.palette.primary.contrastText,
    fontWeight: 'bold',
  },
  ratingContainer: {
    display: 'flex',
    flexDirection: 'column',
  },
  ratingContainerSmall: {
    display: 'flex',
    flexDirection: 'row',
    zIndex: 1,
    position: 'relative',
  },
  ratingActions: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  ratingVoteDown: {
    color: theme.palette.common.white,
    '&:hover': {
      color: red[300],
    },
  },
  ratingVoteUp: {
    color: theme.palette.common.white,
    '&:hover': {
      color: green[300],
    },
  },
  missingMedia: {
    height: '100%',
    color: theme.palette.grey[500],
    display: 'flex',
    backgroundColor: theme.palette.grey[300],
    fontSize: '10em',
  },
  missingMediaIcon: {
    alignSelf: 'center',
    margin: '0 auto',
    display: 'inline-block',
  },
}));

interface ItemCardProps {
  key: string | number;
  item: Item;
  userSelf?: UserSelf;

  // display props
  hoverAddToList: boolean;
  hoverDelete?: boolean;
  hoverWatch?: boolean;
  withActionButton: boolean;

  gridProps?: GridProps;
  // If defined, we're viewing this item within the context of _this_ list
  // This is probably not scalable, but it'll work for now.
  listContext?: List;
  hasLoaded?: () => void;
}

type RequiredThingType = ThingLikeStruct & Linkable & HasImagery;

interface DispatchProps {
  ListUpdate: (payload: ListTrackingUpdatedInitiatedPayload) => void;
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

type Props = ItemCardProps & DispatchProps;

function ItemCard(props: Props) {
  const classes = useStyles();
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState<
    boolean
  >(false);
  const [isHovering, setIsHovering] = useState<boolean>(false);
  const [hoverRating, setHoverRating] = useState<boolean>(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState<boolean>(
    false,
  );
  const [deleted, setDeleted] = useState<boolean>(false);
  const [currentId, setCurrentId] = useState<string>('');
  const [imageLoaded, setImageLoaded] = useState<boolean>(false);

  useEffect(() => {
    let { item } = props;
    let itemId = item.id;
    setCurrentId(itemId);

    if (
      !props.listContext &&
      props.withActionButton &&
      process.env.NODE_ENV !== 'production'
    ) {
      console.warn('withActionButton=true without listContext will not work.');
    }
  }, []);

  useEffect(() => {
    if (props.hasLoaded && imageLoaded) {
      props.hasLoaded();
    }
  }, [imageLoaded]);

  let gridProps: GridProps = {
    item: true,
    ...GRID_COLUMNS,
    ...props.gridProps,
  };
  const itemRef = useRef<HTMLDivElement>(null);
  const loadWrapperRef = useRef<HTMLDivElement>(null);

  const getPlaceholderHeight = (): number => {
    if (itemRef && itemRef.current) {
      // Using aspect ratio height calculation from here:
      // https://www.themoviedb.org/bible/image/59f7582c9251416e7100005f
      const posterAspectRatio = 1.5;
      return Number(itemRef.current.offsetWidth * posterAspectRatio);
    } else {
      return 250;
    }
  };

  const isInViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: '0px',
      threshold: 0,
    },
    targetRef: loadWrapperRef,
    useLazyLoad: true,
  });

  const isNearViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: `${getPlaceholderHeight() / 2}px`,
      threshold: 0,
    },
    targetRef: loadWrapperRef,
    useLazyLoad: true,
  });

  const handleRemoveFromList = () => {
    props.ListUpdate({
      itemId: props.item.id,
      addToLists: [],
      removeFromLists: [props.listContext!.id.toString()],
    });
    setDeleted(true);
    setDeleteConfirmationOpen(false);
  };

  const toggleItemWatched = () => {
    let payload = {
      itemId: currentId,
      action: ActionType.Watched,
    };

    if (checkItemHasTag(ACTION_WATCHED)) {
      props.removeUserItemTags(payload);
    } else {
      props.updateUserItemTags(payload);
      setHoverRating(true);
    }
  };

  const toggleItemRating = (rating: number) => {
    let payload = {
      itemId: currentId,
      action: ActionType.Enjoyed,
      value: rating,
    };

    // Currently no way to 'unrate' an item so no need to remove UserItemTags like we do for 'watched'
    props.updateUserItemTags(payload);

    setIsHovering(false);
    setHoverRating(false);
  };

  const checkItemHasTag = (tagName: string) => {
    if (props.item) {
      return itemHasTag(props.item, ActionType[tagName]);
    }

    return false;
  };

  const renderPoster = (item: Item) => {
    const WrappedCardMedia = React.forwardRef(({ onClick, href }: any, ref) => {
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          style={{ display: 'block', height: '100%', textDecoration: 'none' }}
        >
          <CardMedia
            src={imagePlaceholder}
            item={item}
            component={ResponsiveImage}
            imageType="poster"
            pictureStyle={{
              width: '100%',
              objectFit: 'cover',
              height: '100%',
              display: 'block',
            }}
            imageStyle={{
              width: '100%',
              objectFit: 'cover',
              height: '100%',
              position: 'absolute',
              top: 0,
              left: 0,
              bottom: 0,
              right: 0,
            }}
            loadCallback={() => setImageLoaded(true)}
          />
        </a>
      );
    });

    return (
      <div
        className={isHovering ? classes.cardHoverEnter : classes.cardHoverExit}
      >
        {isHovering && hoverRating && renderRatingHover()}
        {isHovering && !hoverRating && renderHoverActions()}

        <RouterLink href={item.canonicalUrl} as={item.relativeUrl} passHref>
          <WrappedCardMedia />
        </RouterLink>
      </div>
    );
  };

  const renderDialog = () => {
    return (
      <Dialog
        open={deleteConfirmationOpen}
        onClose={() => setDeleteConfirmationOpen(false)}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">{'Remove from List'}</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            Are you sure you want to remove this from your list?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setDeleteConfirmationOpen(false)}
            color="primary"
          >
            Cancel
          </Button>
          <Button onClick={handleRemoveFromList} color="primary" autoFocus>
            Remove
          </Button>
        </DialogActions>
      </Dialog>
    );
  };

  const renderHoverActions = () => {
    let { hoverAddToList, hoverDelete, hoverWatch, userSelf } = props;
    const tooltipPlacement = 'right';

    return (
      <Collapse in={true} style={{ position: 'absolute', top: 0 }}>
        <div className={classes.hoverActions}>
          {userSelf && hoverWatch && (
            <Tooltip
              title={
                checkItemHasTag(ACTION_WATCHED)
                  ? 'Mark as not watched'
                  : 'Mark as watched'
              }
              placement={tooltipPlacement}
            >
              <IconButton
                aria-label="Watched"
                onClick={toggleItemWatched}
                disableRipple
              >
                <Check
                  className={
                    checkItemHasTag(ACTION_WATCHED)
                      ? classes.hoverWatchInvert
                      : classes.hoverWatch
                  }
                />
                <Typography variant="srOnly">
                  {checkItemHasTag(ACTION_WATCHED)
                    ? 'Mark as not watched'
                    : 'Mark as watched'}
                </Typography>
              </IconButton>
            </Tooltip>
          )}

          {hoverAddToList && (
            <Tooltip title="Manage Tracking" placement={tooltipPlacement}>
              <IconButton
                aria-label="Manage Tracking"
                onClick={() => setManageTrackingModalOpen(true)}
                disableRipple
                disableFocusRipple
              >
                <PlaylistAdd className={classes.hoverWatch} />
                <Typography variant="srOnly">Manage Tracking</Typography>
              </IconButton>
            </Tooltip>
          )}

          {isHovering && checkItemHasTag(ACTION_WATCHED) && (
            <Tooltip title={'Rate it!'} placement={tooltipPlacement}>
              <IconButton
                aria-label="Rate it!"
                onClick={() => setHoverRating(true)}
              >
                {checkItemHasTag(ACTION_ENJOYED) ? (
                  <ThumbUp className={classes.hoverRatingThumbsUp} />
                ) : (
                  <ThumbDown className={classes.hoverRatingThumbsDown} />
                )}
                <Typography variant="srOnly">Rate it!</Typography>
              </IconButton>
            </Tooltip>
          )}

          {hoverDelete && (
            <Tooltip title={'Remove from List'} placement={tooltipPlacement}>
              <IconButton
                aria-label="Delete"
                className={classes.hoverDelete}
                onClick={() => setDeleteConfirmationOpen(true)}
                disableRipple
              >
                <DeleteIcon />
                <Typography variant="srOnly">Delete from List</Typography>
              </IconButton>
            </Tooltip>
          )}
        </div>
      </Collapse>
    );
  };

  const renderRatingHover = () => {
    const tooltipPlacement = 'bottom';

    return (
      <div className={classes.ratingHover}>
        <IconButton
          onClick={() => setHoverRating(false)}
          style={{
            position: 'absolute',
            top: 0,
            right: 0,
            zIndex: 1,
          }}
        >
          <Close
            style={{
              color: '#fff',
            }}
          />
        </IconButton>
        <div className={classes.ratingContainer}>
          <Typography className={classes.ratingTitle}>
            What'd ya think?
          </Typography>
          <div className={classes.ratingActions}>
            <Zoom in={hoverRating}>
              <Tooltip title={'Meh'} placement={tooltipPlacement}>
                <IconButton
                  aria-label="Didn't Like It"
                  onClick={() => toggleItemRating(0)}
                >
                  <ThumbDown className={classes.ratingVoteDown} />
                  <Typography variant="srOnly">Mark as disliked</Typography>
                </IconButton>
              </Tooltip>
            </Zoom>
            <Zoom in={hoverRating}>
              <Tooltip title={'Liked it!'} placement={tooltipPlacement}>
                <IconButton
                  aria-label="Liked It"
                  onClick={() => toggleItemRating(1)}
                >
                  <ThumbUp className={classes.ratingVoteUp} />
                  <Typography variant="srOnly">Mark as liked</Typography>
                </IconButton>
              </Tooltip>
            </Zoom>
          </div>
        </div>
      </div>
    );
  };

  return (
    <React.Fragment>
      <Fade
        /*
        The fade will not start until the image container is
        entering the viewport & image has successfuly loaded in,
        ensuring the fade is visible to user.
      */
        in={isInViewport && imageLoaded}
        timeout={1000}
        ref={loadWrapperRef}
      >
        <Grid
          key={!deleted ? props.item.id : `${props.item.id}-deleted`}
          {...gridProps}
        >
          {/* //imageLoaded ? '100%' : getPlaceholderHeight(), */}
          <Card
            style={{
              display: 'flex',
              flexDirection: 'column',
              position: 'relative',
              paddingTop: '150%', // 150% is a magic number for our 1:1.5 expected poster aspect ratio
            }}
            onMouseEnter={isMobile ? undefined : () => setIsHovering(true)}
            onMouseLeave={isMobile ? undefined : () => setIsHovering(false)}
            ref={itemRef}
          >
            {/* No network call is made until container is entering the viewport. */}
            {isNearViewport && renderPoster(props.item)}
          </Card>
        </Grid>
      </Fade>
      <AddToListDialog
        open={manageTrackingModalOpen}
        onClose={() => setManageTrackingModalOpen(false)}
        userSelf={props.userSelf}
        item={props.item}
      />
      {renderDialog()}
    </React.Fragment>
  );
}

ItemCard.defaultProps = {
  withActionButton: false,
  hoverDelete: false,
  hoverWatch: true,
  hoverAddToList: true,
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListUpdate: updateListTracking,
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default connect(null, mapDispatchToProps)(ItemCard);
