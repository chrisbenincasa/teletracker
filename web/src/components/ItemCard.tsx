import {
  Button,
  Card,
  CardContent,
  CardMedia,
  Collapse,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Fade,
  Grid,
  IconButton,
  Theme,
  Tooltip,
  Typography,
  WithStyles,
  withStyles,
  Zoom,
} from '@material-ui/core';
import { green, red } from '@material-ui/core/colors';
import { Link as RouterLink } from 'react-router-dom';
import React, { Component } from 'react';
import Truncate from 'react-truncate';
import AddToListDialog from './AddToListDialog';
import { ActionType, List } from '../types';
import { bindActionCreators, Dispatch } from 'redux';
import { ListUpdate, ListUpdatedInitiatedPayload } from '../actions/lists';
import { connect } from 'react-redux';
import { GridProps } from '@material-ui/core/Grid';
import {
  Check,
  Close,
  Delete as DeleteIcon,
  PlaylistAdd,
  ThumbDown,
  ThumbUp,
} from '@material-ui/icons';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import { ResponsiveImage } from './ResponsiveImage';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import { UserSelf } from '../reducers/user';
import { ACTION_ENJOYED, ACTION_WATCHED } from '../actions/item-detail';
import {
  HasDescription,
  itemHasTag,
  Linkable,
  ThingLikeStruct,
} from '../types/Thing';
import HasImagery from '../types/HasImagery';

const styles = (theme: Theme) =>
  createStyles({
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
  });

type RequiredThingType = ThingLikeStruct &
  HasDescription &
  Linkable &
  HasImagery;

interface ItemCardProps extends WithStyles<typeof styles> {
  key: string | number;
  item: RequiredThingType;
  userSelf: UserSelf | null;

  // display props
  itemCardVisible?: boolean;
  hoverAddToList: boolean;
  hoverDelete?: boolean;
  hoverWatch?: boolean;
  withActionButton: boolean;

  gridProps?: GridProps;
  // If defined, we're viewing this item within the context of _this_ list
  // This is probably not scalable, but it'll work for now.
  listContext?: List;
}

interface DispatchProps {
  ListUpdate: (payload: ListUpdatedInitiatedPayload) => void;
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

interface ItemCardState {
  manageTrackingModalOpen: boolean;
  isHovering: boolean;
  hoverRating: boolean;
  deleteConfirmationOpen: boolean;
  deleted: boolean;
  currentId: string;
  currentType: string;
  imageLoaded: boolean;
}

type Props = ItemCardProps & DispatchProps;

class ItemCard extends Component<Props, ItemCardState> {
  static defaultProps = {
    withActionButton: false,
    itemCardVisible: true,
    hoverDelete: false,
    hoverWatch: true,
    hoverAddToList: true,
  };

  state: ItemCardState = {
    manageTrackingModalOpen: false,
    isHovering: false,
    hoverRating: false,
    deleteConfirmationOpen: false,
    deleted: false,
    currentId: '',
    currentType: '',
    imageLoaded: false,
  };

  constructor(props: Props) {
    super(props);
    if (
      !props.listContext &&
      props.withActionButton &&
      process.env.NODE_ENV !== 'production'
    ) {
      console.warn('withActionButton=true without listContext will not work.');
    }
  }

  componentDidMount() {
    let { item } = this.props;
    let itemId = item.id;
    console.log(item);

    this.setState({
      currentId: itemId,
    });
  }

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  handleDeleteModalOpen = () => {
    this.setState({ deleteConfirmationOpen: true });
  };

  handleDeleteModalClose = () => {
    this.setState({ deleteConfirmationOpen: false });
  };

  handleHoverEnter = () => {
    this.setState({ isHovering: true });
  };

  handleHoverExit = () => {
    this.setState({ isHovering: false });
  };

  handleHoverRatingOpen = () => {
    this.setState({ hoverRating: true });
  };

  handleHoverRatingClose = () => {
    this.setState({ hoverRating: false });
  };

  handleRemoveFromList = () => {
    this.props.ListUpdate({
      thingId: this.props.item.id,
      addToLists: [],
      removeFromLists: [this.props.listContext!.id.toString()],
    });
    this.setState({ deleted: true });
    this.handleDeleteModalClose();
  };

  toggleItemWatched = () => {
    let payload = {
      thingId: this.state.currentId,
      action: ActionType.Watched,
    };

    if (this.itemHasTag(ACTION_WATCHED)) {
      this.props.removeUserItemTags(payload);
    } else {
      this.props.updateUserItemTags(payload);
      this.setState({ hoverRating: true });
      this.handleHoverRatingOpen();
    }
  };

  toggleItemRating = (rating: number) => {
    let payload = {
      thingId: this.state.currentId,
      action: ActionType.Enjoyed,
      value: rating,
    };

    // Currently no way to 'unrate' an item so no need to remove UserItemTags like we do for 'watched'
    this.props.updateUserItemTags(payload);

    this.setState({ isHovering: false, hoverRating: false });
  };

  itemHasTag = (tagName: string) => {
    if (this.props.item) {
      return itemHasTag(this.props.item, ActionType[tagName]);
    }

    return false;
  };

  renderPoster = (thing: RequiredThingType) => {
    let { classes } = this.props;
    let { isHovering, hoverRating } = this.state;

    return (
      <div
        className={isHovering ? classes.cardHoverEnter : classes.cardHoverExit}
      >
        {isHovering && hoverRating && this.renderRatingHover()}
        {isHovering && !hoverRating && this.renderHoverActions()}

        <RouterLink
          to={thing.relativeUrl}
          style={{ display: 'block', height: '100%', textDecoration: 'none' }}
        >
          <CardMedia
            src={imagePlaceholder}
            item={thing}
            component={ResponsiveImage}
            imageType="poster"
            imageStyle={{ width: '100%', objectFit: 'cover', height: '100%' }}
          />
        </RouterLink>
      </div>
    );
  };

  renderDialog() {
    let { deleteConfirmationOpen } = this.state;

    return (
      <Dialog
        open={deleteConfirmationOpen}
        onClose={this.handleDeleteModalClose}
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
          <Button onClick={this.handleDeleteModalClose} color="primary">
            Cancel
          </Button>
          <Button onClick={this.handleRemoveFromList} color="primary" autoFocus>
            Remove
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  renderHoverActions = () => {
    let { classes, hoverAddToList, hoverDelete, hoverWatch, item } = this.props;
    let { isHovering } = this.state;
    let transitionDelay = 100;
    const tooltipPlacement = 'right';

    return (
      <Collapse in={true}>
        <div className={classes.hoverActions}>
          {hoverWatch && (
            <Zoom in={isHovering}>
              <Tooltip
                title={
                  this.itemHasTag(ACTION_WATCHED)
                    ? 'Mark as not watched'
                    : 'Mark as watched'
                }
                placement={tooltipPlacement}
              >
                <IconButton
                  aria-label="Watched"
                  onClick={this.toggleItemWatched}
                  disableRipple
                >
                  <Check
                    className={
                      this.itemHasTag(ACTION_WATCHED)
                        ? classes.hoverWatchInvert
                        : classes.hoverWatch
                    }
                  />
                  <Typography variant="srOnly">
                    {this.itemHasTag(ACTION_WATCHED)
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
                  onClick={this.openManageTrackingModal}
                  disableRipple
                >
                  <PlaylistAdd className={classes.hoverWatch} />
                  <Typography variant="srOnly">Manage Lists</Typography>
                </IconButton>
              </Tooltip>
            </Zoom>
          )}

          {isHovering && this.itemHasTag(ACTION_WATCHED) && (
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
                  onClick={this.handleHoverRatingOpen}
                >
                  {this.itemHasTag(ACTION_ENJOYED) ? (
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
                  onClick={this.handleDeleteModalOpen}
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

  renderRatingHover = () => {
    let { classes } = this.props;
    let { hoverRating } = this.state;
    const tooltipPlacement = 'bottom';

    return (
      <div className={classes.ratingHover}>
        <IconButton
          onClick={() => this.setState({ hoverRating: false })}
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
                  onClick={() => this.toggleItemRating(0)}
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
                  onClick={() => this.toggleItemRating(1)}
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

  render() {
    let {
      classes,
      hoverAddToList,
      item,
      itemCardVisible,
      userSelf,
    } = this.props;
    let { deleted, manageTrackingModalOpen } = this.state;

    let gridProps: Partial<GridProps> = {
      item: true,
      xs: 4,
      sm: 4,
      md: 3,
      lg: 2,
      ...this.props.gridProps,
    };

    return (
      <React.Fragment>
        <Fade in={true} timeout={1000}>
          <Grid key={!deleted ? item.id : 'deleted'} {...gridProps}>
            <Card
              className={classes.card}
              onMouseEnter={this.handleHoverEnter}
              onMouseLeave={this.handleHoverExit}
            >
              {this.renderPoster(item)}
              {itemCardVisible && (
                <CardContent className={classes.cardContent}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      margin: '-8px -8px 0 0',
                    }}
                  >
                    <Typography
                      className={classes.title}
                      variant="h5"
                      component="h2"
                      title={item.name}
                    >
                      {item.name}
                    </Typography>
                  </div>
                  <Typography style={{ height: '60px' }}>
                    <Truncate lines={3} ellipsis={<span>...</span>}>
                      {item.description}
                    </Truncate>
                  </Typography>
                </CardContent>
              )}
            </Card>
          </Grid>
        </Fade>
        {hoverAddToList ? (
          <AddToListDialog
            open={manageTrackingModalOpen}
            onClose={this.closeManageTrackingModal.bind(this)}
            userSelf={userSelf!}
            item={item}
          />
        ) : null}
        {this.renderDialog()}
      </React.Fragment>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListUpdate,
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    null,
    mapDispatchToProps,
  )(ItemCard),
);
