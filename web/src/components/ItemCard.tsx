import React, { RefObject, useEffect, useRef, useState } from 'react';
import {
  Button,
  Card,
  CardMedia,
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
  CheckCircleTwoTone,
  Close,
  Delete as DeleteIcon,
  ThumbDown,
  ThumbUp,
  Visibility,
} from '@material-ui/icons';
import RouterLink from 'next/link';
import { updateListTracking } from '../actions/lists';
import { removeUserItemTags, updateUserItemTags } from '../actions/user';
import { GRID_COLUMNS } from '../constants/';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import { ActionType, List } from '../types';
import AddToListDialog from './Dialogs/AddToListDialog';
import { ResponsiveImage } from './ResponsiveImage';
import {
  getItemTagNumberValue,
  Item,
  itemBelongsToLists,
  itemHasTag,
} from '../types/v2/Item';
import useIntersectionObserver from '../hooks/useIntersectionObserver';
import { useWidth } from '../hooks/useWidth';
import { hexToRGB } from '../utils/style-utils';
import { useDispatchAction } from '../hooks/useDispatchAction';
import dequal from 'dequal';
import AuthDialog from './Auth/AuthDialog';
import { Id } from '../types/v2';
import { createSelector } from 'reselect';
import { AppState } from '../reducers';
import useStateSelector from '../hooks/useStateSelector';
import { hookDeepEqual } from '../hooks/util';
import _ from 'lodash';
import { useWithUserContext } from '../hooks/useWithUser';
import classNames from 'classnames';

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
  ratingHover: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-around',
    flex: '1 0 auto',
    alignItems: 'center',
    overflow: 'hidden',
    position: 'absolute',
    top: 0,
    zIndex: theme.zIndex.appBar - 1,
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
  statusIconContainer: {
    display: 'flex',
    flexDirection: 'row',
    position: 'absolute',
    bottom: 0,
    right: 0,
    zIndex: theme.zIndex.appBar - 2, // show up under hover
    margin: theme.spacing(1),
  },
  statusIcon: {
    position: 'relative',
    cursor: 'pointer',
    marginLeft: theme.spacing(0.5),
    transition: 'all .2s ease-in',
    filter: 'drop-shadow( 0 0 4px rgba(0, 0, 0, 1))',
    color: theme.palette.secondary,
    '&:hover': {
      color: theme.palette.action.active,
    },
  },
  statusIconEnabled: {
    color: theme.palette.action.active,
    opacity: 1.0,
  },
  statusIconDisabled: {
    color: theme.palette.secondary,
    opacity: 0.4,
    '&:hover': {
      color: theme.palette.action.active,
      opacity: 1.0,
    },
  },
}));

interface Props {
  key: string | number;
  itemId: Id;

  // display props
  showDelete?: boolean;
  withActionButton: boolean;

  gridProps?: GridProps;
  // If defined, we're viewing this item within the context of _this_ list
  // This is probably not scalable, but it'll work for now.
  listContext?: List;
  hasLoaded?: () => void;
}

const selectItem = createSelector(
  (state: AppState) => state.itemDetail.thingsById,
  (_, itemId: Id) => itemId,
  (itemsById, itemId) => {
    return itemsById[itemId];
  },
);

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
  const [imageLoaded, setImageLoaded] = useState<boolean>(false);
  const [loginModalOpen, setLoginModalOpen] = useState<boolean>(false);
  const item = useStateSelector(
    state => selectItem(state, props.itemId),
    hookDeepEqual,
  );
  const { isLoggedIn } = useWithUserContext();

  const updateList = useDispatchAction(updateListTracking);
  const updateUserTags = useDispatchAction(updateUserItemTags);
  const removeUserTags = useDispatchAction(removeUserItemTags);

  const itemWatched = itemHasTag(item, ActionType.Watched);
  const userItemRating = getItemTagNumberValue(item, ActionType.Enjoyed);

  useEffect(() => {
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
    updateList({
      itemId: item.id,
      addToLists: [],
      removeFromLists: [props.listContext!.id.toString()],
    });
    setDeleted(true);
    setDeleteConfirmationOpen(false);
  };

  const toggleItemWatched = (): void => {
    let payload = {
      itemId: props.itemId,
      action: ActionType.Watched,
    };

    if (!isLoggedIn) {
      setLoginModalOpen(true);
    } else {
      if (itemWatched) {
        removeUserTags(payload);
      } else {
        updateUserTags(payload);
        setHoverRating(true);
      }
    }
  };

  const toggleItemRating = (rating: number) => {
    let payload = {
      itemId: props.itemId,
      action: ActionType.Enjoyed,
      value: rating,
    };

    if (userItemRating === rating) {
      removeUserTags(payload);
    } else {
      updateUserTags(payload);
    }

    setIsHovering(false);
    setHoverRating(false);
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
      <div>
        {isHovering && hoverRating && renderRatingHover()}
        {renderStatusIcons()}
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

  const renderStatusIcons = () => {
    let { showDelete } = props;
    const itemType = item.type || 'item';
    const rating = userItemRating === 1 ? 'liked' : 'disliked';
    const belongsToLists: string[] = item ? itemBelongsToLists(item) : [];
    const watchedTitle = itemWatched
      ? `You've watched this ${itemType}`
      : `Mark this ${itemType} as watched`;
    const trackedTitle =
      belongsToLists.length > 0
        ? `This ${itemType} is tracked in one of your lists`
        : `Add this ${itemType} to one of your lists`;
    const ratingTitle = `You ${rating} this ${itemType}`;
    const deleteTitle = `Remove this ${itemType} from this list`;

    return (
      <div className={classes.statusIconContainer}>
        {showDelete && (
          <Tooltip title={deleteTitle} placement={'top'}>
            <div>
              <DeleteIcon
                className={classes.statusIcon}
                onClick={() => setDeleteConfirmationOpen(true)}
                color={'action'}
              />
              <Typography variant="srOnly">{deleteTitle}</Typography>
            </div>
          </Tooltip>
        )}
        {!isMobile &&
        itemWatched &&
        (userItemRating === 0 || userItemRating === 1) ? (
          <Tooltip title={ratingTitle} placement={'top'}>
            <div>
              {userItemRating === 1 ? (
                <ThumbUp
                  className={classNames(
                    classes.statusIcon,
                    classes.statusIconEnabled,
                  )}
                  onClick={() => setHoverRating(true)}
                />
              ) : (
                <ThumbDown
                  className={classNames(
                    classes.statusIcon,
                    classes.statusIconEnabled,
                  )}
                  onClick={() => setHoverRating(true)}
                />
              )}
              <Typography variant="srOnly">{ratingTitle}</Typography>
            </div>
          </Tooltip>
        ) : null}

        <Tooltip title={watchedTitle} placement={'top'}>
          <div>
            <Visibility
              className={classNames(
                classes.statusIcon,
                itemWatched
                  ? classes.statusIconEnabled
                  : classes.statusIconDisabled,
              )}
              onClick={toggleItemWatched}
            />
            <Typography variant="srOnly">{watchedTitle}</Typography>
          </div>
        </Tooltip>

        <Tooltip title={trackedTitle} placement={'top'}>
          <div>
            <CheckCircleTwoTone
              className={classNames(
                classes.statusIcon,
                isMobile || isHovering ? classes.statusIconShadow : null,
                belongsToLists.length > 0
                  ? classes.statusIconEnabled
                  : classes.statusIconDisabled,
              )}
              onClick={() => setManageTrackingModalOpen(true)}
            />
            <Typography variant="srOnly">{trackedTitle}</Typography>
          </div>
        </Tooltip>
      </div>
    );
  };

  const renderRatingHover = () => {
    const tooltipPlacement = 'bottom';
    const itemType = item.type || '';

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
            {`Did you like this ${itemType}?`}
          </Typography>
          <div className={classes.ratingActions}>
            <Zoom in={hoverRating}>
              <Tooltip title={'Disliked it'} placement={tooltipPlacement}>
                <IconButton
                  aria-label="Disliked it"
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
                  aria-label="Liked it"
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
      {!deleted && (
        <Fade
          /*
        The fade will not start until the image container is
        entering the viewport & image has successfuly loaded in,
        ensuring the fade is visible to user.
      */
          in={isInViewport && !_.isUndefined(item) && imageLoaded}
          timeout={1000}
          ref={loadWrapperRef}
        >
          <Grid
            key={!deleted ? props.itemId : `${props.itemId}-deleted`}
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
              {isNearViewport && renderPoster(item)}
            </Card>
          </Grid>
        </Fade>
      )}
      <AuthDialog
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
      />
      <AddToListDialog
        open={manageTrackingModalOpen}
        onClose={() => setManageTrackingModalOpen(false)}
        item={item}
      />
      {renderDialog()}
    </React.Fragment>
  );
}

ItemCard.defaultProps = {
  withActionButton: false,
  showDelete: false,
};

// ItemCard.whyDidYouRender = true;

export default React.memo(ItemCard, dequal);
