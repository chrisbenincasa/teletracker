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
import React, { useEffect, useState, useRef } from 'react';
import { connect } from 'react-redux';
import { Link as RouterLink } from 'react-router-dom';
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
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import { UserSelf } from '../reducers/user';
import { ActionType, List } from '../types';
import HasImagery from '../types/HasImagery';
import { Linkable, ThingLikeStruct } from '../types/Thing';
import AddToListDialog from './AddToListDialog';
import { ResponsiveImage } from './ResponsiveImage';
import { ApiItem } from '../types/v2';
import { itemHasTag, Item } from '../types/v2/Item';
import useIntersectionObserver from '../hooks/useIntersectionObserver';

const useStyles = makeStyles((theme: Theme) => ({
  title: {
    flex: 1,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  card: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
  },
  cardContent: {
    flexGrow: 1,
  },
  cardHoverEnter: {
    overflow: 'hidden',
    width: '100%',
    height: '100%',
    background: 'rgba(0, 0, 0, 0.9)',
    display: 'block',
    zIndex: 1,
  },
  cardHoverExit: {
    display: 'block',
    width: '100%',
    height: '100%',
    opacity: 1,
    overflow: 'hidden',
    zIndex: 1,
  },
  hoverActions: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    position: 'absolute',
    top: 0,
    left: 0,
    zIndex: 1,
    background: 'rgba(0, 0, 0, 0.5)',
  },
  hoverDelete: {
    color: '#fff',
    '&:hover': {
      color: red[300],
    },
  },
  hoverWatch: {
    color: '#fff',
    '&:hover': {
      color: green[300],
    },
  },
  hoverWatchInvert: {
    color: green[300],
    '&:hover': {
      color: '#fff',
    },
  },
  hoverRatingThumbsDown: {
    color: '#fff',
    '&:hover': {
      color: red[300],
    },
  },
  hoverRatingThumbsUp: {
    color: '#fff',
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
    background: 'rgba(0, 0, 0, 0.75)',
  },
  ratingTitle: {
    color: '#fff',
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
    color: '#fff',
    '&:hover': {
      color: red[300],
    },
  },
  ratingVoteUp: {
    color: '#fff',
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

  let gridProps: GridProps = {
    item: true,
    ...GRID_COLUMNS,
    ...props.gridProps,
  };

  const loadWrapperRef = useRef(null);
  const isInViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: '50px',
      threshold: 0,
    },
    targetRef: loadWrapperRef,
    useLazyLoad: true,
  });

  const handleRemoveFromList = () => {
    props.ListUpdate({
      thingId: props.item.id,
      addToLists: [],
      removeFromLists: [props.listContext!.id.toString()],
    });
    setDeleted(true);
    setDeleteConfirmationOpen(false);
  };

  const toggleItemWatched = () => {
    let payload = {
      thingId: currentId,
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
      thingId: currentId,
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
    return (
      <div
        className={isHovering ? classes.cardHoverEnter : classes.cardHoverExit}
      >
        {isHovering && hoverRating && renderRatingHover()}
        {isHovering && !hoverRating && renderHoverActions()}

        <RouterLink
          // TODO Fix
          to={'/' + item.type + '/' + item.slug}
          style={{ display: 'block', height: '100%', textDecoration: 'none' }}
        >
          <CardMedia
            src={imagePlaceholder}
            item={item}
            component={ResponsiveImage}
            imageType="poster"
            imageStyle={{ width: '100%', objectFit: 'cover', height: '100%' }}
          />
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

    let transitionDelay = 100;
    const tooltipPlacement = 'right';

    return (
      <Collapse in={true}>
        <div className={classes.hoverActions}>
          {userSelf && hoverWatch && (
            <Zoom in={isHovering}>
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
            </Zoom>
          )}

          {hoverAddToList && (
            <Zoom
              in={isHovering}
              style={{
                transitionDelay: isHovering ? `${transitionDelay}ms` : '0ms',
              }}
            >
              <Tooltip title="Manage Lists" placement={tooltipPlacement}>
                <IconButton
                  aria-label="Manage Lists"
                  onClick={() => setManageTrackingModalOpen(true)}
                  disableRipple
                >
                  <PlaylistAdd className={classes.hoverWatch} />
                  <Typography variant="srOnly">Manage Lists</Typography>
                </IconButton>
              </Tooltip>
            </Zoom>
          )}

          {isHovering && checkItemHasTag(ACTION_WATCHED) && (
            <Zoom
              in={isHovering}
              style={{
                transitionDelay: isHovering
                  ? `${(transitionDelay += 100)}ms`
                  : '100ms',
              }}
            >
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
            </Zoom>
          )}

          {hoverDelete && (
            <Zoom
              in={isHovering}
              style={{
                transitionDelay: isHovering
                  ? `${(transitionDelay += 100)}ms`
                  : '100ms',
              }}
            >
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
            </Zoom>
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
      <Fade in={isInViewport} timeout={1000} ref={loadWrapperRef}>
        <Grid
          key={!deleted ? props.item.id : `${props.item.id}-deleted`}
          {...gridProps}
        >
          <Card
            className={classes.card}
            onMouseEnter={() => setIsHovering(true)}
            onMouseLeave={() => setIsHovering(false)}
          >
            {renderPoster(props.item)}
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

export default connect(
  null,
  mapDispatchToProps,
)(ItemCard);
