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
  Icon,
  IconButton,
  Link,
  Theme,
  Tooltip,
  Typography,
  WithStyles,
  withStyles,
  Zoom,
} from '@material-ui/core';
import { green, red } from '@material-ui/core/colors';
import { GridProps } from '@material-ui/core/Grid';
import Check from '@material-ui/icons/Check';
import DeleteIcon from '@material-ui/icons/Delete';
import PlaylistAdd from '@material-ui/icons/PlaylistAdd';
import ThumbDown from '@material-ui/icons/ThumbDown';
import ThumbUp from '@material-ui/icons/ThumbUp';
import * as R from 'ramda';
import React, { Component, ReactNode } from 'react';
import { connect } from 'react-redux';
import { Link as RouterLink } from 'react-router-dom';
import Truncate from 'react-truncate';
import { bindActionCreators, Dispatch } from 'redux';
import { ListUpdate, ListUpdatedInitiatedPayload } from '../actions/lists';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import withUser, { WithUserProps } from '../components/withUser';
import { ActionType, List, Thing, User } from '../types';
import { getDescription, getPosterPath } from '../utils/metadata-access';
import AddToListDialog from './AddToListDialog';

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
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
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
      alignItems: 'flex-end',
      position: 'absolute',
      top: 0,
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

interface ItemCardProps extends WithStyles<typeof styles> {
  key: string | number;
  item: Thing;
  userSelf?: User;

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
  listsModalOpen: boolean;
  isHovering: boolean;
  hoverRating: boolean;
  deleteConfirmationOpen: boolean;
  deleted: boolean;
  currentId: number;
  currentType: string;
  imageLoaded: boolean;
}

type Props = ItemCardProps & DispatchProps & WithUserProps;

class ItemCard extends Component<Props, ItemCardState> {
  static defaultProps = {
    withActionButton: false,
    itemCardVisible: true,
    hoverDelete: false,
    hoverWatch: true,
    hoverAddToList: true,
  };

  state: ItemCardState = {
    listsModalOpen: false,
    isHovering: false,
    hoverRating: false,
    deleteConfirmationOpen: false,
    deleted: false,
    currentId: 0,
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
    let itemId = Number(item.id);

    this.setState({
      currentId: itemId,
    });
  }

  handleListsModalOpen = (item: Thing) => {
    this.setState({ listsModalOpen: true });
  };

  handleListsModalClose = () => {
    this.setState({ listsModalOpen: false });
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

  handleRemoveFromList = () => {
    this.props.ListUpdate({
      thingId: parseInt(this.props.item.id.toString()),
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

    if (this.itemMarkedAsWatched()) {
      this.props.removeUserItemTags(payload);
    } else {
      this.props.updateUserItemTags(payload);
      this.setState({ hoverRating: true });
    }
  };

  toggleItemRating = (rating: number) => {
    this.setState({ isHovering: false, hoverRating: false });
    // TODO: Setup redux to send rating once that's an option
  };

  itemMarkedAsWatched = () => {
    if (this.props.item && this.props.item.userMetadata) {
      return R.any(tag => {
        return tag.action == ActionType.Watched;
      }, this.props.item.userMetadata.tags);
    }

    return false;
  };

  renderPoster = (thing: Thing) => {
    let { classes, hoverDelete, hoverWatch, hoverAddToList } = this.props;
    let { deleted, isHovering, hoverRating } = this.state;
    let poster = getPosterPath(thing);

    const makeLink = (children: ReactNode, className?: string) => (
      <div
        className={isHovering ? classes.cardHoverEnter : classes.cardHoverExit}
      >
        {isHovering && hoverRating && this.renderRatingHover()}
        {isHovering && !hoverRating && this.renderHoverActions()}
        <Link
          className={className}
          component={props => (
            <RouterLink
              {...props}
              to={'/item/' + thing.type + '/' + thing.id}
            />
          )}
          style={{ position: 'relative' }}
        >
          {children}
        </Link>
      </div>
    );

    if (poster) {
      return makeLink(
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />,
      );
    } else {
      return makeLink(
        <div style={{ display: 'flex', width: '100%' }}>
          <Icon
            className={this.props.classes.missingMediaIcon}
            fontSize="inherit"
          >
            broken_image
          </Icon>
        </div>,
        this.props.classes.missingMedia,
      );
    }
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
    const tooltipPlacement = 'right';

    return (
      <Collapse in={true}>
        <div className={classes.hoverActions}>
          {hoverWatch && (
            <Zoom in={isHovering}>
              <Tooltip
                title={
                  this.itemMarkedAsWatched()
                    ? 'Mark as not watched'
                    : 'Mark as watched'
                }
                placement={tooltipPlacement}
              >
                <IconButton
                  aria-label="Delete"
                  onClick={this.toggleItemWatched}
                  disableRipple
                >
                  <Check
                    className={
                      this.itemMarkedAsWatched()
                        ? classes.hoverWatchInvert
                        : classes.hoverWatch
                    }
                  />
                  <Typography variant="srOnly">
                    {this.itemMarkedAsWatched()
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
              style={{ transitionDelay: isHovering ? '100ms' : '0ms' }}
            >
              <Tooltip title="Manage Lists" placement={tooltipPlacement}>
                <IconButton
                  aria-label="Manage Lists"
                  onClick={() => this.handleListsModalOpen(item)}
                  disableRipple
                >
                  <PlaylistAdd className={classes.hoverWatch} />
                  <Typography variant="srOnly">Manage Lists</Typography>
                </IconButton>
              </Tooltip>
            </Zoom>
          )}

          {hoverDelete && (
            <Zoom
              in={isHovering}
              style={{ transitionDelay: isHovering ? '200ms' : '100ms' }}
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
        <div className={classes.ratingContainer}>
          <Typography className={classes.ratingTitle}>
            What'd ya think?
          </Typography>
          <div className={classes.ratingActions}>
            <Zoom in={hoverRating}>
              <Tooltip title={'Meh'} placement={tooltipPlacement}>
                <IconButton
                  aria-label="Didn't Like It"
                  onClick={() => this.toggleItemRating(-1)}
                >
                  <ThumbDown className={classes.ratingVoteDown} />
                  <Typography variant="srOnly">Mark as diliked</Typography>
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
    let { deleted, imageLoaded, listsModalOpen } = this.state;

    let gridProps: Partial<GridProps> = {
      item: true,
      xs: 6,
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
                      {getDescription(item)}
                    </Truncate>
                  </Typography>
                </CardContent>
              )}
            </Card>
          </Grid>
        </Fade>
        {hoverAddToList ? (
          <AddToListDialog
            open={listsModalOpen}
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

export default withUser(
  withStyles(styles)(
    connect(
      null,
      mapDispatchToProps,
    )(ItemCard),
  ),
);
